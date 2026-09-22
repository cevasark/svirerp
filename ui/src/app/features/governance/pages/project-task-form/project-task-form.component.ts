import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ProjectTaskService } from '../../services/project-task.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { ProjectTask, Person } from '../../../../core/models/domain.model';
import { AutocompleteComponent } from '../../../../shared/components/autocomplete/autocomplete.component';

interface ProjectTaskDialogData {
  projectId: string;
  task: ProjectTask | null;
  /** The project's own assignee — prefills a brand-new task's assignee field (still editable/
   *  clearable) so logging several tasks under the same owner doesn't mean re-picking them every
   *  time. Ignored when editing an existing task, which always keeps its own assignee as-is. */
  defaultAssignee?: Person | null;
}

const STATUSES = ['todo', 'in_progress', 'blocked', 'done'] as const;
const STATUS_LABELS: Record<string, string> = {
  todo: 'To Do',
  in_progress: 'In Progress',
  blocked: 'Blocked',
  done: 'Done',
};

function personLabel(p: Person): string {
  return `${p.firstName} ${p.lastName} (${p.email})`;
}

/** No `min-width` on the form wrapper, deliberately — see ProjectFormComponent's class doc. */
@Component({
  selector: 'app-project-task-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    AutocompleteComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Task</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="task-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" />
          @if (form.controls.name.invalid && form.controls.name.touched) {
            <mat-error>Name is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ statusLabels[s] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="person-row">
          <app-autocomplete class="full-width"
              formControlName="assigneeId"
              label="Assignee (optional)"
              [searchFn]="searchByFirstName"
              [displayFn]="personLabel"
              [initialLabel]="assigneeLabel()" />

          <button mat-icon-button type="button" matTooltip="Person not in the list? Add a new one"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

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
    .task-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; }
    .full-width { width: 100%; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class ProjectTaskFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private taskService = inject(ProjectTaskService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<ProjectTaskFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ProjectTaskDialogData>(MAT_DIALOG_DATA);

  private projectId = this.data.projectId;
  private task = this.data.task;
  isEdit = !!this.task;
  saving = signal(false);

  readonly statuses = STATUSES;
  readonly statusLabels = STATUS_LABELS;

  assigneeLabel = signal('');
  personLabel = personLabel;
  searchByFirstName = (q: string) => this.personService.search('firstName', q);

  form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    description: [''],
    status: ['todo'],
    assigneeId: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    if (this.task) {
      this.form.patchValue({
        name: this.task.name,
        description: this.task.description,
        status: this.task.status,
        assigneeId: this.task.assignee?.id ?? null,
      });
      if (this.task.assignee) {
        this.assigneeLabel.set(personLabel(this.task.assignee));
      }
    } else if (this.data.defaultAssignee) {
      // New task, project has an assignee — default to them (still editable/clearable below).
      this.form.patchValue({ assigneeId: this.data.defaultAssignee.id });
      this.assigneeLabel.set(personLabel(this.data.defaultAssignee));
    }
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.form.patchValue({ assigneeId: person.id });
          this.assigneeLabel.set(personLabel(person));
        }
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // Backend only reads .getId() off nested project/assignee references on write.
    const payload = {
      project: { id: this.projectId },
      name: value.name,
      description: value.description || null,
      status: value.status,
      assignee: value.assigneeId ? { id: value.assigneeId } : null,
    } as unknown as Partial<ProjectTask>;

    const op = this.isEdit
      ? this.taskService.update(this.task!.id, payload)
      : this.taskService.create(payload);

    op.subscribe({
      next: created => {
        this.notifications.success(`Task ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(created);
      },
      error: () => this.saving.set(false),
    });
  }
}
