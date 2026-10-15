import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatMenuModule } from '@angular/material/menu';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import { MeetingMinutesService } from '../../services/meeting-minutes.service';
import { ActionItemService } from '../../services/action-item.service';
import { TrusteeService } from '../../services/trustee.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MeetingMinutes, ActionItem, Trustee } from '../../../../core/models/domain.model';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MeetingMinutesFormComponent } from '../meeting-minutes-form/meeting-minutes-form.component';
import { ActionItemFormComponent } from '../action-item-form/action-item-form.component';

const PRIORITIES = ['high', 'normal', 'low'] as const;
const STATUSES = ['new', 'planned', 'done'] as const;
const DUE_DATE_DEFAULT_DAYS = 7;

const STATUS_LABELS: Record<string, string> = {
  new: 'New',
  planned: 'Planned',
  done: 'Done',
};

const PRIORITY_LABELS: Record<string, string> = {
  high: 'High',
  normal: 'Normal',
  low: 'Low',
};

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function defaultDueDate(): string {
  const date = new Date();
  date.setDate(date.getDate() + DUE_DATE_DEFAULT_DAYS);
  return toIsoDate(date);
}

@Component({
  selector: 'app-meeting-minutes-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PageHeaderComponent,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
    MatMenuModule,
  ],
  template: `
    @if (minutes(); as m) {
      <div class="page-container">
        <div class="top-nav">
          <a mat-button routerLink="/governance/meeting-minutes" class="back-link">
            <mat-icon>arrow_back</mat-icon>
            All Meeting Minutes
          </a>
        </div>

        <app-page-header [title]="m.title" [subtitle]="'Meeting Date: ' + m.meetingDate">
          <ng-container extraActions>
            <button mat-stroked-button (click)="editMinutes()">
              <mat-icon>edit</mat-icon>
              Edit Minutes
            </button>
          </ng-container>
        </app-page-header>

        @if (m.summary) {
          <mat-card class="summary-card">
            <mat-card-header>
              <mat-card-title class="card-section-title">
                <mat-icon class="section-icon">description</mat-icon>
                Summary & Notes
              </mat-card-title>
            </mat-card-header>
            <mat-card-content class="summary-content">{{ m.summary }}</mat-card-content>
          </mat-card>
        }

        <div class="section-header">
          <div class="title-with-badge">
            <h2 class="mat-headline-6 section-title">Action Items</h2>
            <span class="count-pill">{{ actionItems().length }}</span>
          </div>
          <button mat-stroked-button color="primary" (click)="toggleAddForm()">
            <mat-icon>{{ showAddForm() ? 'expand_less' : 'add' }}</mat-icon>
            {{ showAddForm() ? 'Hide Entry Form' : 'Add Action Item' }}
          </button>
        </div>

        @if (showAddForm()) {
          <mat-card class="add-card">
            <mat-card-header>
              <mat-card-title class="card-section-title">
                <mat-icon class="section-icon">playlist_add</mat-icon>
                New Action Item
              </mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <form [formGroup]="addForm" (ngSubmit)="addItem()" class="add-form">
                <div class="form-grid">
                  <mat-form-field appearance="outline" class="col-full">
                    <mat-label>Action Item</mat-label>
                    <input matInput [formControl]="addForm.controls.note"
                           placeholder="e.g., Prepare annual parish financial audit" />
                    @if (addForm.controls.note.invalid && addForm.controls.note.touched) {
                      <mat-error>Action item is required</mat-error>
                    }
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="col-field">
                    <mat-label>Assignee</mat-label>
                    <mat-select [formControl]="addForm.controls.assigneeTrusteeId">
                      <mat-option value="">Unassigned</mat-option>
                      @for (trustee of trustees(); track trustee.id) {
                        <mat-option [value]="trustee.id">{{ trustee.person.firstName }} {{ trustee.person.lastName }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="col-field">
                    <mat-label>Priority</mat-label>
                    <mat-select [formControl]="addForm.controls.priority">
                      @for (p of priorities; track p) {
                        <mat-option [value]="p">{{ priorityLabels[p] }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="col-field">
                    <mat-label>Due Date</mat-label>
                    <input matInput type="date" [formControl]="addForm.controls.dueDate" />
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="col-field">
                    <mat-label>Status</mat-label>
                    <mat-select [formControl]="addForm.controls.status">
                      @for (s of statuses; track s) {
                        <mat-option [value]="s">{{ statusLabels[s] }}</mat-option>
                      }
                    </mat-select>
                  </mat-form-field>

                  <mat-form-field appearance="outline" class="col-full">
                    <mat-label>Additional Notes (optional)</mat-label>
                    <textarea matInput [formControl]="addForm.controls.notes"
                              placeholder="Context, background details, links, or follow-up notes..." rows="3"></textarea>
                  </mat-form-field>
                </div>

                <div class="form-actions">
                  <button mat-flat-button color="primary" type="submit" [disabled]="adding()">
                    <mat-icon>add</mat-icon>
                    Add Action Item
                  </button>
                </div>
              </form>
            </mat-card-content>
          </mat-card>
        }

        @if (isMobile()) {
          <div class="mobile-card-list">
            @for (item of actionItems(); track item.id) {
              <mat-card class="action-card">
                <mat-card-content>
                  <div class="card-top-row">
                    <span class="card-item-note">{{ item.note }}</span>
                    <button type="button" class="status-chip" [attr.data-status]="item.status"
                            [matMenuTriggerFor]="mobileStatusMenu" matTooltip="Tap to change status">
                      {{ statusLabels[item.status] }}
                      <mat-icon class="chip-dropdown-icon">arrow_drop_down</mat-icon>
                    </button>
                    <mat-menu #mobileStatusMenu="matMenu">
                      @for (s of statuses; track s) {
                        <button mat-menu-item [disabled]="s === item.status" (click)="changeItemStatus(item, s)">
                          {{ statusLabels[s] }}
                        </button>
                      }
                    </mat-menu>
                  </div>

                  <div class="card-meta-row">
                    <div class="meta-tag">
                      <mat-icon class="meta-icon">person</mat-icon>
                      <span>{{ getAssigneeName(item) }}</span>
                    </div>
                    <div class="meta-tag">
                      <span class="priority-badge" [attr.data-priority]="item.priority">
                        {{ priorityLabels[item.priority] }}
                      </span>
                    </div>
                    @if (item.dueDate) {
                      <div class="meta-tag">
                        <mat-icon class="meta-icon">event</mat-icon>
                        <span [class.overdue]="isOverdue(item)">Due {{ item.dueDate }}</span>
                      </div>
                    }
                  </div>

                  @if (item.notes) {
                    <div class="card-notes">
                      <div class="notes-caption">Additional Notes:</div>
                      <div>{{ item.notes }}</div>
                    </div>
                  }
                </mat-card-content>

                <mat-card-actions class="card-actions">
                  <button mat-stroked-button (click)="editItem(item)">
                    <mat-icon>edit</mat-icon>
                    Edit
                  </button>
                  <button mat-button color="warn" (click)="confirmDeleteItem(item)">
                    <mat-icon>delete</mat-icon>
                    Delete
                  </button>
                </mat-card-actions>
              </mat-card>
            } @empty {
              <div class="empty-state">
                <mat-icon class="empty-icon">assignment_turned_in</mat-icon>
                <p>No action items yet. Use the form above to add one.</p>
              </div>
            }
          </div>
        } @else {
          <div class="table-container">
            <table class="action-items-table">
              <thead>
                <tr>
                  <th>Action Item</th>
                  <th>Assignee</th>
                  <th>Priority</th>
                  <th>Due Date</th>
                  <th>Status</th>
                  <th>Notes</th>
                  <th class="actions-cell">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (item of actionItems(); track item.id) {
                  <tr>
                    <td class="note-cell">{{ item.note }}</td>
                    <td class="assignee-cell">
                      <div class="assignee-wrap">
                        <mat-icon class="cell-icon">person</mat-icon>
                        <span>{{ getAssigneeName(item) }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="priority-badge" [attr.data-priority]="item.priority">
                        {{ priorityLabels[item.priority] }}
                      </span>
                    </td>
                    <td>
                      @if (item.dueDate) {
                        <span class="due-date-text" [class.overdue]="isOverdue(item)">
                          <mat-icon class="cell-icon">event</mat-icon>
                          {{ item.dueDate }}
                        </span>
                      } @else {
                        <span class="muted">—</span>
                      }
                    </td>
                    <td>
                      <button type="button" class="status-chip" [attr.data-status]="item.status"
                              [matMenuTriggerFor]="desktopStatusMenu" matTooltip="Click to change status">
                        {{ statusLabels[item.status] }}
                        <mat-icon class="chip-dropdown-icon">arrow_drop_down</mat-icon>
                      </button>
                      <mat-menu #desktopStatusMenu="matMenu">
                        @for (s of statuses; track s) {
                          <button mat-menu-item [disabled]="s === item.status" (click)="changeItemStatus(item, s)">
                            {{ statusLabels[s] }}
                          </button>
                        }
                      </mat-menu>
                    </td>
                    <td class="notes-cell">{{ item.notes || '—' }}</td>
                    <td class="actions-cell">
                      <button mat-icon-button matTooltip="Edit item" (click)="editItem(item)">
                        <mat-icon>edit</mat-icon>
                      </button>
                      <button mat-icon-button matTooltip="Delete item" (click)="confirmDeleteItem(item)">
                        <mat-icon>delete</mat-icon>
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="7" class="empty-cell">
                      <div class="assignee-wrap" style="justify-content: center;">
                        <mat-icon class="empty-icon">assignment_turned_in</mat-icon>
                        <span>No action items yet. Use the form above to add one.</span>
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    .top-nav { margin-bottom: 8px; }
    .back-link { color: rgba(0,0,0,.7); text-decoration: none; display: inline-flex; align-items: center; gap: 4px; }
    .summary-card, .add-card { margin-bottom: 16px; box-sizing: border-box; }
    .card-section-title { font-size: 1.05rem; font-weight: 500; display: flex; align-items: center; gap: 8px; margin: 0; }
    .section-icon { color: #3f51b5; font-size: 20px; width: 20px; height: 20px; }
    .summary-content { white-space: pre-wrap; word-break: break-word; line-height: 1.5; margin-top: 6px; }

    .section-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin: 20px 0 10px; }
    .title-with-badge { display: flex; align-items: center; gap: 8px; }
    .section-title { margin: 0; }
    .count-pill { background: rgba(0,0,0,.08); font-size: .8rem; font-weight: 600; padding: 2px 8px; border-radius: 12px; }

    .form-grid { display: flex; flex-wrap: wrap; gap: 10px; width: 100%; box-sizing: border-box; padding-top: 6px; }
    .col-full { flex: 1 1 100%; width: 100%; min-width: 0; }
    .col-field { flex: 1 1 160px; min-width: 130px; }
    .form-actions { display: flex; justify-content: flex-end; margin-top: 6px; }
    @media (max-width: 600px) { .form-actions button { width: 100%; } }

    .table-container { width: 100%; overflow-x: auto; -webkit-overflow-scrolling: touch; border: 1px solid rgba(0,0,0,.1); border-radius: 8px; margin-bottom: 16px; }
    .action-items-table { width: 100%; border-collapse: collapse; min-width: 680px; }
    .action-items-table th { text-align: left; font-weight: 600; font-size: .85rem; padding: 10px 12px; border-bottom: 2px solid rgba(0,0,0,.1); background: #fafafa; }
    .action-items-table td { padding: 8px 12px; vertical-align: middle; border-bottom: 1px solid rgba(0,0,0,.06); font-size: .9rem; }
    .note-cell { font-weight: 500; word-break: break-word; max-width: 260px; }
    .assignee-cell { white-space: nowrap; }
    .assignee-wrap, .meta-tag, .due-date-text { display: flex; align-items: center; gap: 4px; }
    .cell-icon, .meta-icon { font-size: 18px; width: 18px; height: 18px; color: rgba(0,0,0,.54); }
    .notes-cell { word-break: break-word; max-width: 240px; color: rgba(0,0,0,.7); white-space: pre-wrap; }
    .actions-cell { white-space: nowrap; text-align: right; width: 88px; }
    .overdue { color: #d32f2f; font-weight: 600; }
    .muted { color: rgba(0,0,0,.38); }

    .status-chip { display: inline-flex; align-items: center; gap: 2px; padding: 2px 6px 2px 10px; border-radius: 12px; font-size: .78rem; font-weight: 500; border: none; cursor: pointer; white-space: nowrap; font-family: inherit; }
    .status-chip[data-status="new"] { background: #ede7f6; color: #512da8; }
    .status-chip[data-status="planned"] { background: #e3f2fd; color: #1565c0; }
    .status-chip[data-status="done"] { background: #e8f5e9; color: #2e7d32; }
    .status-chip:hover { filter: brightness(.92); }
    .chip-dropdown-icon { font-size: 16px; width: 16px; height: 16px; margin-left: -2px; }

    .priority-badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: .75rem; font-weight: 600; text-transform: uppercase; }
    .priority-badge[data-priority="high"] { background: #ffebee; color: #c62828; }
    .priority-badge[data-priority="normal"] { background: #f0f4f8; color: #37474f; }
    .priority-badge[data-priority="low"] { background: #f5f5f5; color: #757575; }

    .mobile-card-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 16px; }
    .action-card { width: 100%; box-sizing: border-box; }
    .card-top-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
    .card-item-note { font-weight: 600; font-size: .95rem; word-break: break-word; flex: 1; }
    .card-meta-row { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 6px; font-size: .85rem; color: rgba(0,0,0,.65); }
    .card-notes { margin-top: 6px; padding: 6px 8px; background: rgba(0,0,0,.03); border-radius: 4px; font-size: .85rem; word-break: break-word; white-space: pre-wrap; }
    .notes-caption { font-weight: 600; color: rgba(0,0,0,.6); font-size: .8rem; margin-bottom: 2px; }
    .card-actions { display: flex; justify-content: flex-end; gap: 6px; padding: 2px 8px 8px; }

    .empty-state { text-align: center; padding: 28px 16px; background: white; border-radius: 8px; border: 1px dashed rgba(0,0,0,.15); color: rgba(0,0,0,.54); }
    .empty-icon { font-size: 36px; width: 36px; height: 36px; color: rgba(0,0,0,.38); }
    .empty-cell { text-align: center; padding: 24px; color: rgba(0,0,0,.54); }
  `],
})
export class MeetingMinutesDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private fb = inject(FormBuilder);
  private meetingMinutesService = inject(MeetingMinutesService);
  private actionItemService = inject(ActionItemService);
  private trusteeService = inject(TrusteeService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);
  private breakpoint = inject(BreakpointObserver);

  private meetingMinutesId = this.route.snapshot.paramMap.get('id')!;

  minutes = signal<MeetingMinutes | null>(null);
  actionItems = signal<ActionItem[]>([]);
  trustees = signal<Trustee[]>([]);
  adding = signal(false);
  showAddForm = signal(true);

  isMobile = toSignal(
    this.breakpoint.observe(Breakpoints.Handset).pipe(map(r => r.matches)),
    { initialValue: false },
  );

  readonly priorities = PRIORITIES;
  readonly statuses = STATUSES;
  readonly statusLabels = STATUS_LABELS;
  readonly priorityLabels = PRIORITY_LABELS;

  addForm = this.fb.nonNullable.group({
    note: ['', Validators.required],
    assigneeTrusteeId: [''],
    priority: ['normal' as ActionItem['priority']],
    dueDate: [defaultDueDate()],
    status: ['new' as ActionItem['status']],
    notes: [''],
  });

  ngOnInit(): void {
    this.loadMinutes();
    this.loadActionItems();
  }

  toggleAddForm(): void {
    this.showAddForm.update(v => !v);
  }

  getAssigneeName(item: ActionItem): string {
    return item.assigneeTrustee
      ? `${item.assigneeTrustee.person.firstName} ${item.assigneeTrustee.person.lastName}`
      : 'Unassigned';
  }

  isOverdue(item: ActionItem): boolean {
    if (!item.dueDate || item.status === 'done') return false;
    const today = new Date().toISOString().slice(0, 10);
    return item.dueDate < today;
  }

  editMinutes(): void {
    const m = this.minutes();
    if (!m) return;
    this.dialog
      .open(MeetingMinutesFormComponent, {
        width: '540px',
        maxWidth: '95vw',
        data: { minutes: m },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadMinutes(); });
  }

  changeItemStatus(item: ActionItem, newStatus: ActionItem['status']): void {
    if (item.status === newStatus) return;
    const previousStatus = item.status;
    this.actionItems.set(
      this.actionItems().map(i => (i.id === item.id ? { ...i, status: newStatus } : i)),
    );

    const payload = {
      meetingMinutes: { id: this.meetingMinutesId },
      assigneeTrustee: item.assigneeTrustee ? { id: item.assigneeTrustee.id } : null,
      note: item.note,
      priority: item.priority,
      dueDate: item.dueDate || null,
      status: newStatus,
      notes: item.notes || null,
    } as unknown as Partial<ActionItem>;

    this.actionItemService.update(item.id, payload).subscribe({
      next: updated => {
        this.actionItems.set(
          this.actionItems().map(i => (i.id === item.id ? updated : i)),
        );
        this.notifications.success(`Status updated to ${this.statusLabels[newStatus] || newStatus}.`);
      },
      error: () => {
        this.actionItems.set(
          this.actionItems().map(i => (i.id === item.id ? { ...i, status: previousStatus } : i)),
        );
        this.notifications.error('Failed to update status.');
      },
    });
  }

  addItem(): void {
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      return;
    }
    this.adding.set(true);
    const value = this.addForm.getRawValue();
    const payload = {
      meetingMinutes: { id: this.meetingMinutesId },
      assigneeTrustee: value.assigneeTrusteeId ? { id: value.assigneeTrusteeId } : null,
      note: value.note,
      priority: value.priority,
      dueDate: value.dueDate || null,
      status: value.status,
      notes: value.notes || null,
    } as unknown as Partial<ActionItem>;

    this.actionItemService.create(payload).subscribe({
      next: created => {
        this.actionItems.set([...this.actionItems(), created]);
        // Reset fast-changing fields while keeping assignee, priority, status for rapid batch entry
        this.addForm.patchValue({ note: '', dueDate: defaultDueDate(), notes: '' });
        this.addForm.controls.note.markAsUntouched();
        this.adding.set(false);
        this.notifications.success('Action item added.');
      },
      error: () => this.adding.set(false),
    });
  }

  editItem(item: ActionItem): void {
    this.dialog
      .open(ActionItemFormComponent, {
        width: '540px',
        maxWidth: '95vw',
        data: { meetingMinutesId: this.meetingMinutesId, trustees: this.trustees(), item },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadActionItems(); });
  }

  confirmDeleteItem(item: ActionItem): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        maxWidth: '95vw',
        data: {
          title: 'Delete Action Item',
          message: 'Delete this action item? This cannot be undone.',
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteItem(item); });
  }

  private loadMinutes(): void {
    this.meetingMinutesService.getById(this.meetingMinutesId).subscribe({
      next: minutes => {
        this.minutes.set(minutes);
        this.trusteeService.getPage({ page: 0, size: 200 }).subscribe(page => {
          this.trustees.set(page.content.filter(t => t.isActive));
        });
      },
    });
  }

  private loadActionItems(): void {
    this.actionItemService.getForMeeting(this.meetingMinutesId).subscribe(items => {
      this.actionItems.set(items);
    });
  }

  private deleteItem(item: ActionItem): void {
    this.actionItemService.remove(item.id).subscribe({
      next: () => {
        this.actionItems.set(this.actionItems().filter(i => i.id !== item.id));
        this.notifications.success('Action item deleted.');
      },
    });
  }
}
