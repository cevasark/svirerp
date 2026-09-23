import { Component, inject, ChangeDetectionStrategy, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MeetingMinutesService } from '../../services/meeting-minutes.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { MeetingMinutes } from '../../../../core/models/domain.model';

interface MeetingMinutesDialogData {
  minutes: MeetingMinutes | null;
}

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

@Component({
  selector: 'app-meeting-minutes-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Meeting Minutes</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="minutes-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Meeting Date</mat-label>
          <input matInput type="date" formControlName="meetingDate" />
          @if (form.controls.meetingDate.invalid && form.controls.meetingDate.touched) {
            <mat-error>Meeting date is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" />
          @if (form.controls.title.invalid && form.controls.title.touched) {
            <mat-error>Title is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Summary</mat-label>
          <textarea matInput formControlName="summary" rows="4"></textarea>
        </mat-form-field>

      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="20" mode="indeterminate" />
        } @else {
          {{ isEdit ? 'Save Changes' : 'Create' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .minutes-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 400px; }
    .full-width { width: 100%; }
  `],
})
export class MeetingMinutesFormComponent {
  private fb = inject(FormBuilder);
  private meetingMinutesService = inject(MeetingMinutesService);
  private dialogRef = inject(MatDialogRef<MeetingMinutesFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<MeetingMinutesDialogData>(MAT_DIALOG_DATA);

  private minutes = this.data.minutes;
  isEdit = !!this.minutes;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    meetingDate: [this.minutes?.meetingDate ?? toIsoDate(new Date()), Validators.required],
    title: [this.minutes?.title ?? '', Validators.required],
    summary: [this.minutes?.summary ?? ''],
  });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // The backend only reads `.getId()` off the nested org reference, so an
    // { id } stub is all that's needed here.
    const payload = {
      meetingDate: value.meetingDate,
      title: value.title,
      summary: value.summary || null,
    } as unknown as Partial<MeetingMinutes>;

    const op = this.isEdit
      ? this.meetingMinutesService.update(this.minutes!.id, payload)
      : this.meetingMinutesService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Meeting minutes ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
