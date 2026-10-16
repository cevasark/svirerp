import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { RoomRentalService } from '../../services/room-rental.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { RoomRental, RoomRentalStatus } from '../../../../core/models/domain.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { RoomRentalFormComponent } from './room-rental-form/room-rental-form.component';
import { RentalPaymentDialogComponent } from './rental-payment-dialog/rental-payment-dialog.component';

interface DaySchedule {
  date: Date;
  dateStr: string; // YYYY-MM-DD
  dayName: string; // Mon, Tue...
  dayNum: string;  // 12, 13...
  isToday: boolean;
  rentals: RoomRental[];
}

const STATUS_LABELS: Record<RoomRentalStatus, string> = {
  requested: 'Request Received',
  slot_confirmed: 'Slot Confirmed',
  deposit_paid: 'Spot Reserved',
  confirmed: 'Fully Confirmed',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

@Component({
  selector: 'app-room-rentals',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    PageHeaderComponent,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatSelectModule,
    MatMenuModule,
    MatTooltipModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-container">
      <app-page-header
        title="Room Rentals"
        subtitle="Weekly room schedule, inquiries, and trustee reservation workflows"
        actionLabel="New Rental Request"
        actionIcon="add"
        (action)="openCreateDialog()" />

      <!-- KPI Summary Cards -->
      <div class="kpi-grid">
        <mat-card class="kpi-card">
          <div class="kpi-icon-wrap blue"><mat-icon>calendar_view_week</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-title">This Week's Rentals</span>
            <span class="kpi-value">{{ weeklyRentals().length }}</span>
          </div>
        </mat-card>

        <mat-card class="kpi-card">
          <div class="kpi-icon-wrap amber"><mat-icon>mark_email_unread</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-title">Pending Inquiries</span>
            <span class="kpi-value">{{ countByStatus('requested') }}</span>
          </div>
        </mat-card>

        <mat-card class="kpi-card">
          <div class="kpi-icon-wrap sky"><mat-icon>pending_actions</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-title">Awaiting Deposit</span>
            <span class="kpi-value">{{ countByStatus('slot_confirmed') }}</span>
          </div>
        </mat-card>

        <mat-card class="kpi-card">
          <div class="kpi-icon-wrap green"><mat-icon>verified</mat-icon></div>
          <div class="kpi-info">
            <span class="kpi-title">Spot Reserved / Paid</span>
            <span class="kpi-value">{{ countByStatus('deposit_paid') + countByStatus('confirmed') }}</span>
          </div>
        </mat-card>
      </div>

      <!-- Schedule Week Toolbar -->
      <mat-card class="schedule-toolbar-card">
        <div class="toolbar-content">
          <div class="room-selector">
            <mat-icon class="room-icon">meeting_room</mat-icon>
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="room-select-field">
              <mat-select [(ngModel)]="selectedRoom" (selectionChange)="loadWeekSchedule()">
                <mat-option value="Parish Hall">Parish Hall</mat-option>
                <mat-option value="Fellowship Room">Fellowship Room</mat-option>
                <mat-option value="Community Kitchen">Community Kitchen</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="week-nav">
            <button mat-icon-button (click)="previousWeek()" matTooltip="Previous Week">
              <mat-icon>chevron_left</mat-icon>
            </button>
            <button mat-stroked-button (click)="currentWeek()" class="today-btn">
              Today
            </button>
            <button mat-icon-button (click)="nextWeek()" matTooltip="Next Week">
              <mat-icon>chevron_right</mat-icon>
            </button>
            <span class="week-title">{{ weekRangeLabel() }}</span>
          </div>

          <div class="view-toggle">
            <mat-button-toggle-group [(ngModel)]="viewMode" aria-label="View Mode">
              <mat-button-toggle value="pipeline">
                <mat-icon>view_kanban</mat-icon>
                <span>Pipeline</span>
              </mat-button-toggle>
              <mat-button-toggle value="list">
                <mat-icon>view_list</mat-icon>
                <span>List View</span>
              </mat-button-toggle>
            </mat-button-toggle-group>
          </div>
        </div>
      </mat-card>

      <!-- WEEKLY SCHEDULE GRID -->
      <div class="weekly-grid-container">
        <h3 class="section-title">
          <mat-icon inline>calendar_today</mat-icon>
          Weekly Room Schedule & Visibility ({{ selectedRoom() }})
        </h3>

        <div class="week-grid">
          @for (day of weekDays(); track day.dateStr) {
            <div class="day-col" [class.today-col]="day.isToday">
              <div class="day-header">
                <span class="day-name">{{ day.dayName }}</span>
                <span class="day-num">{{ day.dayNum }}</span>
              </div>

              <div class="day-slots">
                @for (rental of day.rentals; track rental.id) {
                  <div class="rental-slot-card"
                       [attr.data-status]="rental.status"
                       (click)="openEditDialog(rental)">
                    <div class="slot-time">
                      <mat-icon inline class="time-icon">schedule</mat-icon>
                      <span>{{ rental.startTime | date:'shortTime' }} – {{ rental.endTime | date:'shortTime' }}</span>
                    </div>
                    <div class="slot-title">{{ rental.eventTitle }}</div>
                    <div class="slot-renter">
                      <mat-icon inline>person</mat-icon>
                      <span>{{ rental.renterPerson.firstName }} {{ rental.renterPerson.lastName }}</span>
                    </div>
                    <div class="slot-badge" [attr.data-status]="rental.status">
                      {{ statusLabels[rental.status] }}
                    </div>
                  </div>
                } @empty {
                  <div class="empty-day-slot" (click)="openCreateDialog(day.dateStr)">
                    <mat-icon>add_circle_outline</mat-icon>
                    <span>Available</span>
                  </div>
                }
              </div>
            </div>
          }
        </div>
      </div>

      <!-- LOWER VIEW: PIPELINE BOARD vs LIST VIEW -->
      @if (viewMode() === 'pipeline') {
        <div class="pipeline-container">
          <div class="pipeline-header">
            <h3 class="section-title">
              <mat-icon inline>account_tree</mat-icon>
              4-Step Trustee Rental Pipeline
            </h3>
            <span class="pipeline-desc">Manage rental agreements across the 4 stages without unnecessary overhead</span>
          </div>

          <div class="pipeline-columns">
            <!-- STEP 1: REQUESTED -->
            <div class="pipeline-col">
              <div class="col-banner step-1">
                <span class="step-num">1</span>
                <span class="step-title">Inquiries & Requests</span>
                <span class="step-count">{{ requestsList().length }}</span>
              </div>
              <div class="cards-list">
                @for (item of requestsList(); track item.id) {
                  <ng-container *ngTemplateOutlet="rentalCard; context: { $implicit: item, step: 1 }"></ng-container>
                } @empty {
                  <p class="empty-col">No incoming requests</p>
                }
              </div>
            </div>

            <!-- STEP 2: SLOT CONFIRMED -->
            <div class="pipeline-col">
              <div class="col-banner step-2">
                <span class="step-num">2</span>
                <span class="step-title">Slot Confirmed</span>
                <span class="step-count">{{ slotConfirmedList().length }}</span>
              </div>
              <div class="cards-list">
                @for (item of slotConfirmedList(); track item.id) {
                  <ng-container *ngTemplateOutlet="rentalCard; context: { $implicit: item, step: 2 }"></ng-container>
                } @empty {
                  <p class="empty-col">No slots waiting for deposit</p>
                }
              </div>
            </div>

            <!-- STEP 3: SPOT RESERVED (DEPOSIT PAID) -->
            <div class="pipeline-col">
              <div class="col-banner step-3">
                <span class="step-num">3</span>
                <span class="step-title">Spot Reserved (Deposit)</span>
                <span class="step-count">{{ depositPaidList().length }}</span>
              </div>
              <div class="cards-list">
                @for (item of depositPaidList(); track item.id) {
                  <ng-container *ngTemplateOutlet="rentalCard; context: { $implicit: item, step: 3 }"></ng-container>
                } @empty {
                  <p class="empty-col">No active deposits</p>
                }
              </div>
            </div>

            <!-- STEP 4: CONFIRMED -->
            <div class="pipeline-col">
              <div class="col-banner step-4">
                <span class="step-num">4</span>
                <span class="step-title">Fully Confirmed & Paid</span>
                <span class="step-count">{{ confirmedList().length }}</span>
              </div>
              <div class="cards-list">
                @for (item of confirmedList(); track item.id) {
                  <ng-container *ngTemplateOutlet="rentalCard; context: { $implicit: item, step: 4 }"></ng-container>
                } @empty {
                  <p class="empty-col">No confirmed bookings</p>
                }
              </div>
            </div>
          </div>
        </div>
      } @else {
        <!-- LIST VIEW -->
        <mat-card class="list-card">
          <div class="list-filter-bar">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="status-filter">
              <mat-label>Status Filter</mat-label>
              <mat-select [(ngModel)]="statusFilter" (selectionChange)="loadAllRentals()">
                <mat-option value="all">All Statuses</mat-option>
                <mat-option value="requested">1. Request Received</mat-option>
                <mat-option value="slot_confirmed">2. Slot Confirmed</mat-option>
                <mat-option value="deposit_paid">3. Spot Reserved</mat-option>
                <mat-option value="confirmed">4. Fully Confirmed</mat-option>
                <mat-option value="completed">Completed</mat-option>
                <mat-option value="cancelled">Cancelled</mat-option>
              </mat-select>
            </mat-form-field>
          </div>

          <div class="table-container">
            <table class="rentals-table">
              <thead>
                <tr>
                  <th>Event & Room</th>
                  <th>Date & Time</th>
                  <th>Renter (Contact)</th>
                  <th>Trustee POC</th>
                  <th>Financials (Fee / Dep / Paid)</th>
                  <th>Status</th>
                  <th class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (r of allRentals(); track r.id) {
                  <tr>
                    <td>
                      <div class="event-title-wrap">
                        <strong class="clickable" (click)="openEditDialog(r)">{{ r.eventTitle }}</strong>
                        <span class="room-chip">{{ r.roomName }}</span>
                      </div>
                    </td>
                    <td>
                      <div class="datetime-cell">
                        <span>{{ r.startTime | date:'EEE, MMM d, y' }}</span>
                        <span class="time-sub">{{ r.startTime | date:'shortTime' }} – {{ r.endTime | date:'shortTime' }}</span>
                      </div>
                    </td>
                    <td>
                      <div class="renter-cell">
                        <strong>{{ r.renterPerson.firstName }} {{ r.renterPerson.lastName }}</strong>
                        @if (r.renterPerson.phone) {
                          <span class="phone-sub">{{ r.renterPerson.phone }}</span>
                        }
                      </div>
                    </td>
                    <td>
                      @if (r.contactTrustee) {
                        <span class="trustee-badge">
                          <mat-icon inline>person_outline</mat-icon>
                          {{ r.contactTrustee.person.firstName }} {{ r.contactTrustee.person.lastName }}
                        </span>
                      } @else {
                        <span class="muted">—</span>
                      }
                    </td>
                    <td>
                      <div class="fin-cell">
                        <span>Fee: {{ r.totalFee | currency }}</span>
                        <span class="fin-sub">Dep: {{ r.depositAmount | currency }} | Paid: {{ (r.depositAmount + r.finalAmountPaid) | currency }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="status-pill" [attr.data-status]="r.status">
                        {{ statusLabels[r.status] }}
                      </span>
                    </td>
                    <td class="actions-col">
                      <button mat-icon-button [matMenuTriggerFor]="tableMenu">
                        <mat-icon>more_vert</mat-icon>
                      </button>
                      <mat-menu #tableMenu="matMenu">
                        <button mat-menu-item (click)="openEditDialog(r)">
                          <mat-icon>edit</mat-icon> Edit Details
                        </button>
                        @if (r.status === 'requested') {
                          <button mat-menu-item (click)="confirmSlot(r)">
                            <mat-icon>check_circle</mat-icon> Confirm Slot Available
                          </button>
                        }
                        @if (r.status === 'slot_confirmed') {
                          <button mat-menu-item (click)="recordPayment(r, 'deposit')">
                            <mat-icon>payments</mat-icon> Record Deposit ($)
                          </button>
                        }
                        @if (r.status === 'deposit_paid') {
                          <button mat-menu-item (click)="recordPayment(r, 'final')">
                            <mat-icon>paid</mat-icon> Record Final Payment ($)
                          </button>
                        }
                        @if (r.status !== 'cancelled' && r.status !== 'completed') {
                          <button mat-menu-item (click)="cancelRental(r)">
                            <mat-icon>cancel</mat-icon> Cancel Booking
                          </button>
                        }
                        <button mat-menu-item (click)="deleteRental(r)">
                          <mat-icon color="warn">delete</mat-icon> Delete
                        </button>
                      </mat-menu>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="7" class="empty-cell">No room rentals found.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </mat-card>
      }
    </div>

    <!-- KANBAN CARD REUSABLE TEMPLATE -->
    <ng-template #rentalCard let-item let-step="step">
      <div class="kanban-card" [attr.data-status]="item.status">
        <div class="card-header-row">
          <span class="card-date">{{ item.startTime | date:'EEE, MMM d' }}</span>
          <button mat-icon-button class="card-menu-btn" [matMenuTriggerFor]="cardMenu">
            <mat-icon>more_vert</mat-icon>
          </button>
          <mat-menu #cardMenu="matMenu">
            <button mat-menu-item (click)="openEditDialog(item)">
              <mat-icon>edit</mat-icon> Edit Details
            </button>
            @if (item.status !== 'cancelled') {
              <button mat-menu-item (click)="cancelRental(item)">
                <mat-icon>cancel</mat-icon> Cancel Booking
              </button>
            }
            <button mat-menu-item (click)="deleteRental(item)">
              <mat-icon color="warn">delete</mat-icon> Delete
            </button>
          </mat-menu>
        </div>

        <div class="card-title clickable" (click)="openEditDialog(item)">
          {{ item.eventTitle }}
        </div>

        <div class="card-time">
          <mat-icon inline>schedule</mat-icon>
          {{ item.startTime | date:'shortTime' }} – {{ item.endTime | date:'shortTime' }}
        </div>

        <div class="card-renter">
          <mat-icon inline>person</mat-icon>
          <strong>{{ item.renterPerson.firstName }} {{ item.renterPerson.lastName }}</strong>
        </div>

        @if (item.contactTrustee) {
          <div class="card-trustee" matTooltip="Trustee Point of Contact">
            <mat-icon inline>verified_user</mat-icon>
            <span>POC: {{ item.contactTrustee.person.firstName }} {{ item.contactTrustee.person.lastName }}</span>
          </div>
        }

        <div class="card-financials">
          <div class="fin-item">
            <span class="lbl">Fee</span>
            <span class="val">{{ item.totalFee | currency }}</span>
          </div>
          <div class="fin-item">
            <span class="lbl">Deposit</span>
            <span class="val">{{ item.depositAmount | currency }}</span>
          </div>
          <div class="fin-item highlight">
            <span class="lbl">Balance</span>
            <span class="val">{{ (item.totalFee - item.depositAmount - item.finalAmountPaid) | currency }}</span>
          </div>
        </div>

        <!-- STAGE ADVANCEMENT BUTTON -->
        <div class="card-actions">
          @if (step === 1) {
            <button mat-flat-button color="primary" class="stage-btn" (click)="confirmSlot(item)">
              <mat-icon>check</mat-icon> Confirm Slot
            </button>
          } @else if (step === 2) {
            <button mat-flat-button color="accent" class="stage-btn deposit-btn" (click)="recordPayment(item, 'deposit')">
              <mat-icon>payments</mat-icon> Record Deposit
            </button>
          } @else if (step === 3) {
            <button mat-flat-button class="stage-btn confirm-btn" (click)="recordPayment(item, 'final')">
              <mat-icon>paid</mat-icon> Record Balance
            </button>
          } @else if (step === 4) {
            <button mat-stroked-button class="stage-btn view-btn" (click)="openEditDialog(item)">
              <mat-icon>visibility</mat-icon> Details
            </button>
          }
        </div>
      </div>
    </ng-template>
  `,
  styles: [`
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 16px;
      margin-bottom: 16px;
    }
    .kpi-card {
      display: flex;
      flex-direction: row;
      align-items: center;
      gap: 16px;
      padding: 16px;
      border-radius: 8px;
    }
    .kpi-icon-wrap {
      width: 48px;
      height: 48px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .kpi-icon-wrap mat-icon { font-size: 26px; width: 26px; height: 26px; }
    .kpi-icon-wrap.blue { background: #e3f2fd; color: #1565c0; }
    .kpi-icon-wrap.amber { background: #fff8e1; color: #f57f17; }
    .kpi-icon-wrap.sky { background: #e0f2fe; color: #0284c7; }
    .kpi-icon-wrap.green { background: #e8f5e9; color: #2e7d32; }
    .kpi-info { display: flex; flex-direction: column; }
    .kpi-title { font-size: .85rem; color: rgba(0,0,0,.6); }
    .kpi-value { font-size: 1.5rem; font-weight: 700; line-height: 1.2; }

    .schedule-toolbar-card {
      padding: 8px 16px;
      margin-bottom: 16px;
      border-radius: 8px;
    }
    .toolbar-content {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .room-selector {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .room-icon { color: #3f51b5; }
    .room-select-field { width: 190px; }
    .week-nav {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .today-btn { font-weight: 600; }
    .week-title { font-weight: 600; font-size: 1rem; margin-left: 8px; }

    .section-title {
      font-size: 1.1rem;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 12px;
      color: #1e293b;
    }

    /* WEEKLY GRID */
    .weekly-grid-container {
      background: white;
      border: 1px solid rgba(0,0,0,.08);
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 24px;
      box-shadow: 0 1px 3px rgba(0,0,0,.04);
    }
    .week-grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 10px;
      min-width: 840px;
      overflow-x: auto;
    }
    .day-col {
      background: #f8fafc;
      border: 1px solid rgba(0,0,0,.06);
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      min-height: 200px;
    }
    .day-col.today-col {
      border-color: #3b82f6;
      background: #eff6ff;
    }
    .day-header {
      padding: 8px 10px;
      border-bottom: 1px solid rgba(0,0,0,.06);
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-weight: 600;
    }
    .day-name { font-size: .8rem; text-transform: uppercase; color: rgba(0,0,0,.6); }
    .day-num { font-size: 1.05rem; }
    .today-col .day-num {
      background: #2563eb;
      color: white;
      width: 26px;
      height: 26px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .day-slots {
      padding: 8px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      flex: 1;
    }
    .rental-slot-card {
      background: white;
      border: 1px solid rgba(0,0,0,.1);
      border-left: 4px solid #3b82f6;
      border-radius: 6px;
      padding: 8px;
      cursor: pointer;
      transition: transform .15s ease, box-shadow .15s ease;
    }
    .rental-slot-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 6px -1px rgba(0,0,0,.1);
    }
    .rental-slot-card[data-status="requested"] { border-left-color: #f59e0b; }
    .rental-slot-card[data-status="slot_confirmed"] { border-left-color: #0284c7; }
    .rental-slot-card[data-status="deposit_paid"] { border-left-color: #7c3aed; }
    .rental-slot-card[data-status="confirmed"] { border-left-color: #16a34a; }
    .rental-slot-card[data-status="cancelled"] { border-left-color: #94a3b8; opacity: .7; }

    .slot-time { font-size: .75rem; font-weight: 600; color: #475569; display: flex; align-items: center; gap: 4px; }
    .time-icon { font-size: 14px; width: 14px; height: 14px; }
    .slot-title { font-weight: 600; font-size: .85rem; margin: 2px 0; word-break: break-word; }
    .slot-renter { font-size: .75rem; color: rgba(0,0,0,.6); display: flex; align-items: center; gap: 2px; }
    .slot-badge {
      font-size: .7rem;
      font-weight: 600;
      margin-top: 4px;
      display: inline-block;
      padding: 1px 6px;
      border-radius: 4px;
    }
    .slot-badge[data-status="requested"] { background: #fef3c7; color: #b45309; }
    .slot-badge[data-status="slot_confirmed"] { background: #e0f2fe; color: #0369a1; }
    .slot-badge[data-status="deposit_paid"] { background: #ede9fe; color: #6d28d9; }
    .slot-badge[data-status="confirmed"] { background: #dcfce7; color: #15803d; }
    .slot-badge[data-status="cancelled"] { background: #f1f5f9; color: #64748b; }

    .empty-day-slot {
      border: 1px dashed rgba(0,0,0,.15);
      border-radius: 6px;
      padding: 12px 6px;
      text-align: center;
      color: rgba(0,0,0,.4);
      cursor: pointer;
      font-size: .8rem;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      transition: background .15s ease;
    }
    .empty-day-slot:hover {
      background: rgba(0,0,0,.03);
      color: #2563eb;
      border-color: #2563eb;
    }

    /* PIPELINE KANBAN */
    .pipeline-container { margin-bottom: 24px; }
    .pipeline-header { margin-bottom: 12px; }
    .pipeline-desc { font-size: .85rem; color: rgba(0,0,0,.6); }
    .pipeline-columns {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 14px;
      min-width: 900px;
      overflow-x: auto;
    }
    .pipeline-col {
      background: #f1f5f9;
      border-radius: 8px;
      padding: 10px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .col-banner {
      display: flex;
      align-items: center;
      padding: 8px 12px;
      border-radius: 6px;
      font-weight: 600;
      font-size: .9rem;
      gap: 8px;
    }
    .step-num {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      background: rgba(0,0,0,.15);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: .75rem;
    }
    .step-title { flex: 1; }
    .step-count {
      background: white;
      padding: 1px 7px;
      border-radius: 12px;
      font-size: .8rem;
    }
    .step-1 { background: #fef3c7; color: #92400e; }
    .step-2 { background: #e0f2fe; color: #075985; }
    .step-3 { background: #ede9fe; color: #5b21b6; }
    .step-4 { background: #dcfce7; color: #166534; }

    .cards-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .kanban-card {
      background: white;
      border-radius: 8px;
      padding: 12px;
      border: 1px solid rgba(0,0,0,.08);
      box-shadow: 0 1px 3px rgba(0,0,0,.04);
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .card-header-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .card-date {
      font-size: .8rem;
      font-weight: 700;
      color: #2563eb;
    }
    .card-menu-btn { width: 24px; height: 24px; line-height: 24px; }
    .card-title {
      font-weight: 600;
      font-size: .95rem;
      color: #1e293b;
      line-height: 1.3;
    }
    .card-time, .card-renter, .card-trustee {
      font-size: .82rem;
      color: rgba(0,0,0,.65);
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .card-trustee { color: #4338ca; font-weight: 500; }
    .card-financials {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 4px;
      margin-top: 4px;
      padding: 6px;
      background: #f8fafc;
      border-radius: 4px;
      text-align: center;
    }
    .fin-item { display: flex; flex-direction: column; }
    .fin-item .lbl { font-size: .7rem; color: rgba(0,0,0,.5); text-transform: uppercase; }
    .fin-item .val { font-size: .85rem; font-weight: 600; }
    .fin-item.highlight .val { color: #d97706; font-weight: 700; }

    .card-actions { margin-top: 6px; }
    .stage-btn { width: 100%; font-size: .85rem; }
    .deposit-btn { background: #7c3aed !important; color: white !important; }
    .confirm-btn { background: #16a34a !important; color: white !important; }
    .view-btn { border-color: rgba(0,0,0,.2); }

    .empty-col {
      text-align: center;
      font-size: .85rem;
      color: rgba(0,0,0,.4);
      padding: 20px 8px;
    }

    /* LIST VIEW TABLE */
    .list-card { padding: 16px; border-radius: 8px; }
    .list-filter-bar { margin-bottom: 12px; }
    .status-filter { width: 220px; }
    .table-container { overflow-x: auto; }
    .rentals-table { width: 100%; border-collapse: collapse; min-width: 800px; }
    .rentals-table th {
      text-align: left;
      font-weight: 600;
      font-size: .85rem;
      padding: 10px 12px;
      border-bottom: 2px solid rgba(0,0,0,.08);
      background: #fafafa;
    }
    .rentals-table td {
      padding: 10px 12px;
      border-bottom: 1px solid rgba(0,0,0,.06);
      font-size: .9rem;
      vertical-align: middle;
    }
    .event-title-wrap { display: flex; flex-direction: column; gap: 2px; }
    .room-chip { font-size: .75rem; color: rgba(0,0,0,.5); }
    .datetime-cell { display: flex; flex-direction: column; }
    .time-sub, .phone-sub, .fin-sub { font-size: .8rem; color: rgba(0,0,0,.55); }
    .trustee-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: #e0e7ff;
      color: #3730a3;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: .8rem;
      font-weight: 500;
    }
    .status-pill {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 12px;
      font-size: .78rem;
      font-weight: 600;
    }
    .status-pill[data-status="requested"] { background: #fef3c7; color: #b45309; }
    .status-pill[data-status="slot_confirmed"] { background: #e0f2fe; color: #0369a1; }
    .status-pill[data-status="deposit_paid"] { background: #ede9fe; color: #6d28d9; }
    .status-pill[data-status="confirmed"] { background: #dcfce7; color: #15803d; }
    .status-pill[data-status="cancelled"] { background: #f1f5f9; color: #64748b; }
    .status-pill[data-status="completed"] { background: #e2e8f0; color: #334155; }

    .clickable { cursor: pointer; color: #2563eb; }
    .clickable:hover { text-decoration: underline; }
    .actions-col { text-align: right; width: 60px; }
    .muted { color: rgba(0,0,0,.35); }
    .empty-cell { text-align: center; padding: 24px; color: rgba(0,0,0,.5); }
  `],
})
export class RoomRentalsComponent implements OnInit {
  private rentalService = inject(RoomRentalService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  readonly statusLabels = STATUS_LABELS;

  // Selected week baseline (Monday of that week)
  currentWeekMonday = signal<Date>(this.getMonday(new Date()));
  selectedRoom = signal<string>('Parish Hall');
  viewMode = signal<'pipeline' | 'list'>('pipeline');
  statusFilter = signal<string>('all');

  weeklyRentals = signal<RoomRental[]>([]);
  allRentals = signal<RoomRental[]>([]);
  loading = signal(false);

  // Filtered lists for Kanban
  requestsList = computed(() => this.allRentals().filter(r => r.status === 'requested'));
  slotConfirmedList = computed(() => this.allRentals().filter(r => r.status === 'slot_confirmed'));
  depositPaidList = computed(() => this.allRentals().filter(r => r.status === 'deposit_paid'));
  confirmedList = computed(() => this.allRentals().filter(r => r.status === 'confirmed'));

  countByStatus(status: RoomRentalStatus): number {
    return this.allRentals().filter(r => r.status === status).length;
  }

  // Generate 7 days for the weekly calendar
  weekDays = computed<DaySchedule[]>(() => {
    const mon = this.currentWeekMonday();
    const days: DaySchedule[] = [];
    const todayStr = this.formatDateIso(new Date());

    for (let i = 0; i < 7; i++) {
      const d = new Date(mon);
      d.setDate(mon.getDate() + i);
      const dateStr = this.formatDateIso(d);
      const rentalsForDay = this.weeklyRentals().filter(r => {
        const rDate = this.formatDateIso(new Date(r.startTime));
        return rDate === dateStr;
      });

      days.push({
        date: d,
        dateStr,
        dayName: d.toLocaleDateString([], { weekday: 'short' }),
        dayNum: String(d.getDate()),
        isToday: dateStr === todayStr,
        rentals: rentalsForDay,
      });
    }
    return days;
  });

  weekRangeLabel = computed<string>(() => {
    const mon = this.currentWeekMonday();
    const sun = new Date(mon);
    sun.setDate(mon.getDate() + 6);
    const mStr = mon.toLocaleDateString([], { month: 'short', day: 'numeric' });
    const sStr = sun.toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
    return `${mStr} – ${sStr}`;
  });

  ngOnInit(): void {
    this.loadWeekSchedule();
    this.loadAllRentals();
  }

  loadWeekSchedule(): void {
    const mon = this.currentWeekMonday();
    const startIso = new Date(mon.getFullYear(), mon.getMonth(), mon.getDate(), 0, 0, 0).toISOString();
    const sun = new Date(mon);
    sun.setDate(mon.getDate() + 6);
    const endIso = new Date(sun.getFullYear(), sun.getMonth(), sun.getDate(), 23, 59, 59).toISOString();

    this.rentalService.getSchedule(startIso, endIso, this.selectedRoom()).subscribe({
      next: list => this.weeklyRentals.set(list),
    });
  }

  loadAllRentals(): void {
    this.loading.set(true);
    const filter = this.statusFilter() === 'all' ? undefined : this.statusFilter();
    this.rentalService.getFilteredPage(filter, { page: 0, size: 200, sort: 'startTime,asc' }).subscribe({
      next: page => {
        this.allRentals.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  previousWeek(): void {
    const prev = new Date(this.currentWeekMonday());
    prev.setDate(prev.getDate() - 7);
    this.currentWeekMonday.set(prev);
    this.loadWeekSchedule();
  }

  nextWeek(): void {
    const next = new Date(this.currentWeekMonday());
    next.setDate(next.getDate() + 7);
    this.currentWeekMonday.set(next);
    this.loadWeekSchedule();
  }

  currentWeek(): void {
    this.currentWeekMonday.set(this.getMonday(new Date()));
    this.loadWeekSchedule();
  }

  openCreateDialog(defaultDate?: string): void {
    this.dialog
      .open(RoomRentalFormComponent, {
        width: '640px',
        data: { rental: null, defaultDate },
      })
      .afterClosed()
      .subscribe(saved => {
        if (saved) {
          this.loadWeekSchedule();
          this.loadAllRentals();
        }
      });
  }

  openEditDialog(rental: RoomRental): void {
    this.dialog
      .open(RoomRentalFormComponent, {
        width: '640px',
        data: { rental },
      })
      .afterClosed()
      .subscribe(saved => {
        if (saved) {
          this.loadWeekSchedule();
          this.loadAllRentals();
        }
      });
  }

  confirmSlot(rental: RoomRental): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Confirm Slot Availability',
          message: `Confirm that "${rental.roomName}" is available for "${rental.eventTitle}" on ${new Date(rental.startTime).toLocaleDateString()}? This advances the agreement to Step 2 (Awaiting Deposit).`,
          confirmLabel: 'Confirm Slot',
        },
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (confirmed) {
          this.rentalService.update(rental.id, { ...rental, status: 'slot_confirmed' }).subscribe({
            next: () => {
              this.notifications.success('Slot confirmed! Awaiting deposit.');
              this.loadWeekSchedule();
              this.loadAllRentals();
            },
          });
        }
      });
  }

  recordPayment(rental: RoomRental, paymentType: 'deposit' | 'final'): void {
    this.dialog
      .open(RentalPaymentDialogComponent, {
        width: '480px',
        data: { rental, paymentType },
      })
      .afterClosed()
      .subscribe(saved => {
        if (saved) {
          this.loadWeekSchedule();
          this.loadAllRentals();
        }
      });
  }

  cancelRental(rental: RoomRental): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Cancel Rental Booking',
          message: `Are you sure you want to cancel the booking for "${rental.eventTitle}"? The time slot will be released.`,
          confirmLabel: 'Cancel Booking',
        },
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (confirmed) {
          this.rentalService.update(rental.id, { ...rental, status: 'cancelled' }).subscribe({
            next: () => {
              this.notifications.info('Booking cancelled.');
              this.loadWeekSchedule();
              this.loadAllRentals();
            },
          });
        }
      });
  }

  deleteRental(rental: RoomRental): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Rental Agreement',
          message: `Permanently delete the rental record for "${rental.eventTitle}"? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => {
        if (confirmed) {
          this.rentalService.remove(rental.id).subscribe({
            next: () => {
              this.notifications.success('Rental record deleted.');
              this.loadWeekSchedule();
              this.loadAllRentals();
            },
          });
        }
      });
  }

  private getMonday(d: Date): Date {
    const date = new Date(d);
    const day = date.getDay();
    const diff = date.getDate() - day + (day === 0 ? -6 : 1); // adjust when day is sunday
    return new Date(date.setDate(diff));
  }

  private formatDateIso(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
