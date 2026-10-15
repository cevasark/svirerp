import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ActionItemService } from '../../services/action-item.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ActionItem, Trustee } from '../../../../core/models/domain.model';

interface ActionItemDialogData {
  meetingMinutesId: string;
  trustees: Trustee[];
  item: ActionItem;
}

const PRIORITIES = ['high', 'normal', 'low'] as const;
const STATUSES = ['new', 'planned', 'done'] as const;

@Component({
  selector: 'app-action-item-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Edit Action Item</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="item-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Note</mat-label>
          <textarea matInput formControlName="note" rows="3"></textarea>
          @if (form.controls.note.invalid && form.controls.note.touched) {
            <mat-error>Note is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Assignee</mat-label>
          <mat-select formControlName="assigneeTrusteeId">
            <mat-option [value]="''">Unassigned</mat-option>
            @for (trustee of trustees; track trustee.id) {
              <mat-option [value]="trustee.id">{{ trustee.person.firstName }} {{ trustee.person.lastName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Priority</mat-label>
            <mat-select formControlName="priority">
              @for (p of priorities; track p) {
                <mat-option [value]="p">{{ p }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Due Date</mat-label>
            <input matInput type="date" formControlName="dueDate" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              @for (s of statuses; track s) {
                <mat-option [value]="s">{{ s }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Additional Notes</mat-label>
          <textarea matInput formControlName="notes" rows="2"></textarea>
        </mat-form-field>

      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          Save Changes
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .item-form { display: flex; flex-direction: column; gap: 8px; padding-top: 8px; width: 100%; max-width: 100%; box-sizing: border-box; }
    .full-width { width: 100%; }
    .form-row { display: flex; flex-wrap: wrap; gap: 12px; width: 100%; }
    .flex-1 { flex: 1 1 120px; min-width: 100px; }
  `],
})
export class ActionItemFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private actionItemService = inject(ActionItemService);
  private dialogRef = inject(MatDialogRef<ActionItemFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ActionItemDialogData>(MAT_DIALOG_DATA);

  private meetingMinutesId = this.data.meetingMinutesId;
  private item = this.data.item;
  trustees = this.data.trustees;
  saving = signal(false);

  readonly priorities = PRIORITIES;
  readonly statuses = STATUSES;

  form = this.fb.nonNullable.group({
    note: ['', Validators.required],
    assigneeTrusteeId: [''],
    priority: ['normal'],
    dueDate: [''],
    status: ['new'],
    notes: [''],
  });

  ngOnInit(): void {
    this.form.patchValue({
      note: this.item.note,
      assigneeTrusteeId: this.item.assigneeTrustee?.id ?? '',
      priority: this.item.priority,
      dueDate: this.item.dueDate,
      status: this.item.status,
      notes: this.item.notes,
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // The backend only reads `.getId()` off nested meetingMinutes/assigneeTrustee
    // references, so { id } stubs are all that's needed here.
    const payload = {
      meetingMinutes: { id: this.meetingMinutesId },
      assigneeTrustee: value.assigneeTrusteeId ? { id: value.assigneeTrusteeId } : null,
      note: value.note,
      priority: value.priority,
      dueDate: value.dueDate || null,
      status: value.status,
      notes: value.notes || null,
    } as unknown as Partial<ActionItem>;

    this.actionItemService.update(this.item.id, payload).subscribe({
      next: () => {
        this.notifications.success('Action item updated.');
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
