import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';

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
    PageHeaderComponent,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTooltipModule,
  ],
  template: `
    @if (minutes(); as m) {
      <div class="page-container">
        <app-page-header [title]="m.title" [subtitle]="m.meetingDate">
          <ng-container extraActions>
            <button mat-stroked-button (click)="editMinutes()">
              <mat-icon>edit</mat-icon>
              Edit
            </button>
          </ng-container>
        </app-page-header>

        @if (m.summary) {
          <mat-card class="summary-card">
            <mat-card-content>{{ m.summary }}</mat-card-content>
          </mat-card>
        }

        <h2 class="mat-headline-6 section-title">Action Items</h2>

        <table class="action-items-table">
          <thead>
            <tr>
              <th>Note</th>
              <th>Assignee</th>
              <th>Priority</th>
              <th>Due Date</th>
              <th>Status</th>
              <th>Notes</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (item of actionItems(); track item.id) {
              <tr>
                <td class="note-cell">{{ item.note }}</td>
                <td>{{ item.assigneeTrustee ? (item.assigneeTrustee.person.firstName + ' ' + item.assigneeTrustee.person.lastName) : 'Unassigned' }}</td>
                <td>{{ item.priority }}</td>
                <td>{{ item.dueDate || '—' }}</td>
                <td>{{ item.status }}</td>
                <td class="notes-cell">{{ item.notes || '—' }}</td>
                <td class="actions-cell">
                  <button mat-icon-button matTooltip="Edit" (click)="editItem(item)">
                    <mat-icon>edit</mat-icon>
                  </button>
                  <button mat-icon-button matTooltip="Delete" (click)="confirmDeleteItem(item)">
                    <mat-icon>delete</mat-icon>
                  </button>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="7" class="empty-cell">No action items yet.</td>
              </tr>
            }

            <tr class="add-row">
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <textarea matInput [formControl]="addForm.controls.note" placeholder="New action item…" rows="1"></textarea>
                </mat-form-field>
              </td>
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <mat-select [formControl]="addForm.controls.assigneeTrusteeId">
                    <mat-option value="">Unassigned</mat-option>
                    @for (trustee of trustees(); track trustee.id) {
                      <mat-option [value]="trustee.id">{{ trustee.person.firstName }} {{ trustee.person.lastName }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </td>
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <mat-select [formControl]="addForm.controls.priority">
                    @for (p of priorities; track p) {
                      <mat-option [value]="p">{{ p }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </td>
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <input matInput type="date" [formControl]="addForm.controls.dueDate" />
                </mat-form-field>
              </td>
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <mat-select [formControl]="addForm.controls.status">
                    @for (s of statuses; track s) {
                      <mat-option [value]="s">{{ s }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </td>
              <td>
                <mat-form-field appearance="outline" class="full-width no-hint">
                  <input matInput [formControl]="addForm.controls.notes" placeholder="Notes" />
                </mat-form-field>
              </td>
              <td class="actions-cell">
                <button mat-flat-button color="primary" [disabled]="adding()" (click)="addItem()">
                  <mat-icon>add</mat-icon>
                  Add
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .summary-card { margin-bottom: 16px; white-space: pre-wrap; }
    .section-title { margin: 8px 0 12px; }
    .action-items-table { width: 100%; border-collapse: collapse; }
    .action-items-table th { text-align: left; font-weight: 600; padding: 8px; border-bottom: 2px solid rgba(0,0,0,.12); }
    .action-items-table td { padding: 4px 8px; vertical-align: top; border-bottom: 1px solid rgba(0,0,0,.06); }
    .note-cell, .notes-cell { white-space: pre-wrap; max-width: 240px; }
    .actions-cell { white-space: nowrap; text-align: right; }
    .empty-cell { text-align: center; padding: 24px; color: rgba(0,0,0,.54); }
    .add-row td { vertical-align: top; padding-top: 12px; }
    .full-width { width: 100%; }
    .no-hint { margin-bottom: -1.25em; }
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

  private meetingMinutesId = this.route.snapshot.paramMap.get('id')!;

  minutes = signal<MeetingMinutes | null>(null);
  actionItems = signal<ActionItem[]>([]);
  trustees = signal<Trustee[]>([]);
  adding = signal(false);

  readonly priorities = PRIORITIES;
  readonly statuses = STATUSES;

  addForm = this.fb.nonNullable.group({
    note: ['', Validators.required],
    assigneeTrusteeId: [''],
    priority: ['normal'],
    dueDate: [defaultDueDate()],
    status: ['new'],
    notes: [''],
  });

  ngOnInit(): void {
    this.loadMinutes();
    this.loadActionItems();
  }

  editMinutes(): void {
    const m = this.minutes();
    if (!m) return;
    this.dialog
      .open(MeetingMinutesFormComponent, {
        width: '540px',
        data: { minutes: m },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadMinutes(); });
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
        // Reset just the fast-changing fields — priority/status/assignee stay
        // put so logging several similar items in a row stays quick.
        this.addForm.patchValue({ note: '', dueDate: defaultDueDate(), notes: '' });
        this.addForm.controls.note.markAsUntouched();
        this.adding.set(false);
      },
      error: () => this.adding.set(false),
    });
  }

  editItem(item: ActionItem): void {
    this.dialog
      .open(ActionItemFormComponent, {
        width: '540px',
        data: { meetingMinutesId: this.meetingMinutesId, trustees: this.trustees(), item },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadActionItems(); });
  }

  confirmDeleteItem(item: ActionItem): void {
    this.dialog
      .open(ConfirmDialogComponent, {
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
