import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ProjectChecklistService } from '../../services/project-checklist.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ProjectChecklist } from '../../../../core/models/domain.model';

interface ProjectChecklistDialogData {
  projectId: string;
  checklist: ProjectChecklist | null;
}

/** Title/Completion Date creation and editing, in a modal — same pattern as
 *  ProjectTaskFormComponent, so a checklist is created the same way a task is rather than via an
 *  inline form on the project page. Once created, the project page shows title/date read-only;
 *  editing them again goes back through this same dialog (like clicking a task's name). */
@Component({
  selector: 'app-project-checklist-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Checklist</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="checklist-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" />
          @if (form.controls.title.invalid && form.controls.title.touched) {
            <mat-error>Title is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Completion date</mat-label>
          <input matInput type="date" formControlName="completionDate" />
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
    .checklist-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; }
    .full-width { width: 100%; }
  `],
})
export class ProjectChecklistFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private checklistService = inject(ProjectChecklistService);
  private dialogRef = inject(MatDialogRef<ProjectChecklistFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ProjectChecklistDialogData>(MAT_DIALOG_DATA);

  private projectId = this.data.projectId;
  private checklist = this.data.checklist;
  isEdit = !!this.checklist;
  saving = signal(false);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    completionDate: [''],
  });

  ngOnInit(): void {
    if (this.checklist) {
      this.form.patchValue({
        title: this.checklist.title,
        completionDate: this.checklist.completionDate ?? '',
      });
    }
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const payload = { title: value.title, completionDate: value.completionDate || null };

    const op = this.isEdit
      ? this.checklistService.update(this.checklist!.id, payload)
      : this.checklistService.create(this.projectId, payload);

    op.subscribe({
      next: saved => {
        this.notifications.success(`Checklist ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(saved);
      },
      error: () => this.saving.set(false),
    });
  }
}
