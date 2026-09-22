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

import { ProjectService } from '../../services/project.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { Project, Person } from '../../../../core/models/domain.model';
import { AutocompleteComponent } from '../../../../shared/components/autocomplete/autocomplete.component';

interface ProjectDialogData {
  orgId: string;
  project: Project | null;
}

const STATUSES = ['planning', 'in_progress', 'on_hold', 'completed', 'cancelled'] as const;
const STATUS_LABELS: Record<string, string> = {
  planning: 'Planning',
  in_progress: 'In Progress',
  on_hold: 'On Hold',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

function personLabel(p: Person): string {
  return `${p.firstName} ${p.lastName} (${p.email})`;
}

/** No `min-width` on the form wrapper, deliberately — see PersonFormComponent, the reference for
 *  a dialog that stays usable on a narrow mobile viewport (a fixed min-width forces the dialog
 *  wider than the screen, which is what makes most of this app's other "Add" dialogs unusable on
 *  mobile today). */
@Component({
  selector: 'app-project-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Project</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="project-form">

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

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              @for (s of statuses; track s) {
                <mat-option [value]="s">{{ statusLabels[s] }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Due Date</mat-label>
            <input matInput type="date" formControlName="dueDate" />
          </mat-form-field>
        </div>

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
    .project-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class ProjectFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private projectService = inject(ProjectService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<ProjectFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ProjectDialogData>(MAT_DIALOG_DATA);

  private orgId = this.data.orgId;
  private project = this.data.project;
  isEdit = !!this.project;
  saving = signal(false);

  readonly statuses = STATUSES;
  readonly statusLabels = STATUS_LABELS;

  assigneeLabel = signal('');
  personLabel = personLabel;
  searchByFirstName = (q: string) => this.personService.search('firstName', q);

  form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    description: [''],
    status: ['planning'],
    dueDate: [''],
    assigneeId: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    if (this.project) {
      this.form.patchValue({
        name: this.project.name,
        description: this.project.description,
        status: this.project.status,
        dueDate: this.project.dueDate,
        assigneeId: this.project.assignee?.id ?? null,
      });
      if (this.project.assignee) {
        this.assigneeLabel.set(personLabel(this.project.assignee));
      }
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
    // Backend only reads .getId() off nested org/assignee references on write.
    const payload = {
      org: { id: this.orgId },
      name: value.name,
      description: value.description || null,
      status: value.status,
      dueDate: value.dueDate || null,
      assignee: value.assigneeId ? { id: value.assigneeId } : null,
    } as unknown as Partial<Project>;

    const op = this.isEdit
      ? this.projectService.update(this.project!.id, payload)
      : this.projectService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Project ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
