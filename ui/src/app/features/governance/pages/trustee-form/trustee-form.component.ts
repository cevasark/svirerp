import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { TrusteeService } from '../../services/trustee.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { Trustee, Person } from '../../../../core/models/domain.model';
import { AutocompleteComponent } from '../../../../shared/components/autocomplete/autocomplete.component';

interface TrusteeDialogData {
  trustee: Trustee | null;
}

const TERM_YEARS = 2;

function toIsoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function personLabel(p: Person): string {
  return `${p.firstName} ${p.lastName} (${p.email})`;
}

@Component({
  selector: 'app-trustee-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    AutocompleteComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Trustee</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="trustee-form">

        <div class="person-row">
          <app-autocomplete class="full-width"
              formControlName="personId"
              label="Person"
              errorText="Person is required"
              [searchFn]="searchByFirstName"
              [displayFn]="personLabel"
              [initialLabel]="personDisplayLabel()" />

          <button mat-icon-button type="button" matTooltip="Person not in the list? Add a new one"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Title</mat-label>
            <input matInput formControlName="title" placeholder="e.g. Chairperson" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Role</mat-label>
            <input matInput formControlName="role" />
            @if (form.controls.role.invalid && form.controls.role.touched) {
              <mat-error>Role is required</mat-error>
            }
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Term Start</mat-label>
            <input matInput type="date" formControlName="termStart" />
            @if (form.controls.termStart.invalid && form.controls.termStart.touched) {
              <mat-error>Term start is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Term End</mat-label>
            <input matInput type="date" formControlName="termEnd" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Notes</mat-label>
          <textarea matInput formControlName="notes" rows="3"></textarea>
        </mat-form-field>

        <mat-checkbox formControlName="isActive">Active</mat-checkbox>
        <mat-checkbox formControlName="isOfficer">Officer</mat-checkbox>

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
    .trustee-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 400px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class TrusteeFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private trusteeService = inject(TrusteeService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<TrusteeFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<TrusteeDialogData>(MAT_DIALOG_DATA);

  private trustee = this.data.trustee;
  isEdit = !!this.trustee;
  saving = signal(false);

  personDisplayLabel = signal('');
  personLabel = personLabel;
  searchByFirstName = (q: string) => this.personService.search('firstName', q);

  form = this.fb.nonNullable.group({
    personId: ['', Validators.required],
    title: [''],
    role: ['Trustee', Validators.required],
    termStart: [toIsoDate(new Date()), Validators.required],
    termEnd: [toIsoDate(new Date(new Date().setFullYear(new Date().getFullYear() + TERM_YEARS)))],
    notes: [''],
    isActive: [true],
    isOfficer: [false],
  });

  ngOnInit(): void {
    if (this.trustee) {
      this.form.patchValue({
        personId: this.trustee.person.id,
        title: this.trustee.title,
        role: this.trustee.role,
        termStart: this.trustee.termStart,
        termEnd: this.trustee.termEnd,
        notes: this.trustee.notes,
        isActive: this.trustee.isActive,
        isOfficer: this.trustee.isOfficer,
      });
      this.personDisplayLabel.set(personLabel(this.trustee.person));
    }
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.form.patchValue({ personId: person.id });
          this.personDisplayLabel.set(personLabel(person));
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
    // The backend only reads `.getId()` off nested person/org references, so
    // { id } stubs are all that's needed here.
    const payload = {
      person: { id: value.personId },
      title: value.title || null,
      role: value.role,
      termStart: value.termStart,
      termEnd: value.termEnd || null,
      notes: value.notes || null,
      isActive: value.isActive,
      isOfficer: value.isOfficer,
    } as unknown as Partial<Trustee>;

    const op = this.isEdit
      ? this.trusteeService.update(this.trustee!.id, payload)
      : this.trusteeService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Trustee ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
