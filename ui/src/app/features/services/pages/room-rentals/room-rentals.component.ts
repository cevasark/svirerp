import { Component, inject, OnInit, signal, computed, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
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
    MatFormFieldModule,
    MatSelectModule,
    MatMenuModule,
    MatTooltipModule,
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
                    @if (rental.contractUrl) {
                      <div class="slot-contract" (click)="$event.stopPropagation()">
                        <a [href]="rental.contractUrl" target="_blank" rel="noopener noreferrer" class="contract-chip" matTooltip="Open rental contract in new tab">
                          <mat-icon inline>description</mat-icon> Contract
                        </a>
                      </div>
                    }
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

      <!-- ALL RENTALS TABLE VIEW -->
      <mat-card class="list-card">
        <div class="table-controls-bar">
          <div class="filter-group">
            <mat-form-field appearance="outline" subscriptSizing="dynamic" class="status-filter">
              <mat-label>Filter by Status</mat-label>
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

          <div class="sort-group">
            <button mat-stroked-button (click)="toggleDateSort()" class="date-sort-btn"
                    matTooltip="Toggle event date sorting (currently {{ dateSortDirection() === 'desc' ? 'Newest first' : 'Oldest first' }})">
              <mat-icon>{{ dateSortDirection() === 'desc' ? 'arrow_downward' : 'arrow_upward' }}</mat-icon>
              <span>Date: <strong>{{ dateSortDirection() === 'desc' ? 'Newest first (Desc)' : 'Oldest first (Asc)' }}</strong></span>
            </button>
          </div>
        </div>

        <div class="table-container">
          <table class="rentals-table">
            <thead>
              <tr>
                <th class="sortable-th clickable" (click)="toggleDateSort()"
                    matTooltip="Click to sort by event date (currently {{ dateSortDirection() === 'desc' ? 'Newest first' : 'Oldest first' }})">
                  <div class="th-sort-wrap">
                    <span>Event Date & Time</span>
                    <mat-icon class="sort-icon">{{ dateSortDirection() === 'desc' ? 'arrow_downward' : 'arrow_upward' }}</mat-icon>
                    <span class="sort-order-pill">{{ dateSortDirection() === 'desc' ? 'Desc' : 'Asc' }}</span>
                  </div>
                </th>
                <th>Event & Room</th>
                <th>Renter (Contact)</th>
                <th>Trustee POC</th>
                <th>Fee / Deposit / Balance</th>
                <th>Status & Next Step</th>
                <th class="actions-col">Actions</th>
              </tr>
            </thead>
            <tbody>
              @for (r of allRentals(); track r.id) {
                <tr>
                  <td>
                    <div class="datetime-cell">
                      <span class="date-main">{{ r.startTime | date:'EEE, MMM d, y' }}</span>
                      <span class="time-sub">{{ r.startTime | date:'shortTime' }} – {{ r.endTime | date:'shortTime' }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="event-title-wrap">
                      <strong class="clickable" (click)="openEditDialog(r)">{{ r.eventTitle }}</strong>
                      <div class="room-tags">
                        <span class="room-chip">{{ r.roomName }}</span>
                        @if (r.contractUrl) {
                          <a class="contract-chip" [href]="r.contractUrl" target="_blank" rel="noopener noreferrer"
                             matTooltip="Open rental contract in new tab" (click)="$event.stopPropagation()">
                            <mat-icon inline>description</mat-icon> Contract
                          </a>
                        }
                      </div>
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
                      <span class="trustee-badge" matTooltip="{{ r.contactTrustee.role }}">
                        <mat-icon inline>person_outline</mat-icon>
                        {{ r.contactTrustee.person.firstName }} {{ r.contactTrustee.person.lastName }}
                      </span>
                    } @else {
                      <span class="muted">—</span>
                    }
                  </td>
                  <td>
                    <div class="fin-cell">
                      <span>Fee: <strong>{{ r.totalFee | currency }}</strong></span>
                      <span class="fin-sub">
                        Dep: {{ r.depositAmount | currency }} |
                        Bal: <span [class.balance-alert]="(r.totalFee - r.depositAmount - r.finalAmountPaid) > 0">
                          {{ (r.totalFee - r.depositAmount - r.finalAmountPaid) | currency }}
                        </span>
                      </span>
                    </div>
                  </td>
                  <td>
                    <div class="status-action-cell">
                      <span class="status-pill" [attr.data-status]="r.status">
                        {{ statusLabels[r.status] }}
                      </span>

                      @if (r.status === 'requested') {
                        <button mat-stroked-button color="primary" class="row-step-btn" (click)="confirmSlot(r)">
                          <mat-icon>check</mat-icon> Confirm Slot
                        </button>
                      } @else if (r.status === 'slot_confirmed') {
                        <button mat-stroked-button class="row-step-btn deposit-btn" (click)="recordPayment(r, 'deposit')">
                          <mat-icon>payments</mat-icon> Record Deposit
                        </button>
                      } @else if (r.status === 'deposit_paid') {
                        <button mat-stroked-button class="row-step-btn balance-btn" (click)="recordPayment(r, 'final')">
                          <mat-icon>paid</mat-icon> Record Balance
                        </button>
                      }
                    </div>
                  </td>
                  <td class="actions-col">
                    <button mat-icon-button [matMenuTriggerFor]="tableMenu">
                      <mat-icon>more_vert</mat-icon>
                    </button>
                    <mat-menu #tableMenu="matMenu">
                      <button mat-menu-item (click)="openEditDialog(r)">
                        <mat-icon>edit</mat-icon> Edit Details
                      </button>
                      @if (r.contractUrl) {
                        <a mat-menu-item [href]="r.contractUrl" target="_blank" rel="noopener noreferrer">
                          <mat-icon>description</mat-icon> View Rental Contract
                        </a>
                      }
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
                  <td colspan="7" class="empty-cell">
                    @if (loading()) {
                      <mat-progress-spinner diameter="24" mode="indeterminate" class="inline-spinner" />
                      <span>Loading room rentals...</span>
                    } @else {
                      <span>No room rentals found matching current filters.</span>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </mat-card>
    </div>
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
      min-height: 190px;
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

    /* TABLE VIEW */
    .list-card { padding: 16px; border-radius: 8px; }
    .table-controls-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: 12px;
    }
    .status-filter { width: 220px; }
    .date-sort-btn { font-weight: 500; }
    .date-sort-btn strong { color: #2563eb; }

    .table-container { overflow-x: auto; }
    .rentals-table { width: 100%; border-collapse: collapse; min-width: 860px; }
    .rentals-table th {
      text-align: left;
      font-weight: 600;
      font-size: .85rem;
      padding: 10px 12px;
      border-bottom: 2px solid rgba(0,0,0,.08);
      background: #fafafa;
    }
    .sortable-th { cursor: pointer; user-select: none; }
    .sortable-th:hover { background: #f1f5f9; }
    .th-sort-wrap { display: flex; align-items: center; gap: 6px; }
    .sort-icon { font-size: 18px; width: 18px; height: 18px; color: #2563eb; }
    .sort-order-pill {
      background: #e0e7ff;
      color: #3730a3;
      padding: 1px 6px;
      border-radius: 10px;
      font-size: .72rem;
      font-weight: 700;
    }

    .rentals-table td {
      padding: 10px 12px;
      border-bottom: 1px solid rgba(0,0,0,.06);
      font-size: .9rem;
      vertical-align: middle;
    }
    .datetime-cell { display: flex; flex-direction: column; }
    .date-main { font-weight: 600; }
    .time-sub, .phone-sub, .fin-sub { font-size: .8rem; color: rgba(0,0,0,.55); }
    .event-title-wrap { display: flex; flex-direction: column; gap: 2px; }
    .room-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 2px; }
    .contract-chip {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
      padding: 1px 6px;
      border-radius: 4px;
      font-size: .72rem;
      font-weight: 500;
      text-decoration: none;
    }
    .contract-chip:hover {
      background: #dbeafe;
      color: #1e40af;
      text-decoration: none;
    }
    .slot-contract { margin-top: 3px; }
    .room-chip { font-size: .75rem; color: rgba(0,0,0,.5); }
    .renter-cell { display: flex; flex-direction: column; }
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
    .fin-cell { display: flex; flex-direction: column; }
    .balance-alert { color: #d97706; font-weight: 600; }

    .status-action-cell {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
    }
    .row-step-btn {
      font-size: .75rem;
      line-height: 24px;
      padding: 0 8px;
    }
    .deposit-btn { color: #7c3aed !important; border-color: #7c3aed !important; }
    .balance-btn { color: #16a34a !important; border-color: #16a34a !important; }

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
    .inline-spinner { display: inline-block; vertical-align: middle; margin-right: 8px; }
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
  statusFilter = signal<string>('all');
  dateSortDirection = signal<'desc' | 'asc'>('desc');

  weeklyRentals = signal<RoomRental[]>([]);
  allRentals = signal<RoomRental[]>([]);
  loading = signal(false);

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
    const sort = `startTime,${this.dateSortDirection()}`;
    this.rentalService.getFilteredPage(filter, { page: 0, size: 200, sort }).subscribe({
      next: page => {
        this.allRentals.set(page.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  toggleDateSort(): void {
    this.dateSortDirection.update(dir => (dir === 'desc' ? 'asc' : 'desc'));
    this.loadAllRentals();
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
    const diff = date.getDate() - day + (day === 0 ? -6 : 1);
    return new Date(date.setDate(diff));
  }

  private formatDateIso(d: Date): string {
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
