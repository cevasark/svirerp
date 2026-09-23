import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { VolunteerService } from '../../services/volunteer.service';
import { VolunteerAreaService } from '../../services/volunteer-area.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Person, Volunteer, VolunteerArea } from '../../../../core/models/domain.model';
import { AutocompleteComponent } from '../../../../shared/components/autocomplete/autocomplete.component';

function personLabel(p: Person): string {
  return `${p.firstName} ${p.lastName} (${p.email})`;
}

export interface VolunteerFormData {
  entity: Volunteer | null;
}

@Component({
  selector: 'app-volunteer-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    AutocompleteComponent,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Volunteer</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="volunteer-form">

        <div class="form-row">
          <app-autocomplete class="flex-1"
              formControlName="personId"
              label="Volunteer"
              errorText="Select a person"
              [searchFn]="searchByFirstName"
              [displayFn]="personLabel"
              [initialLabel]="volunteerLabel()" />
          <button mat-icon-button type="button" matTooltip="Add new person"
                  (click)="addPerson('volunteer')">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <div class="form-row">
          <app-autocomplete class="flex-1"
              formControlName="contactPersonId"
              label="Contact Person"
              [searchFn]="searchByFirstName"
              [displayFn]="personLabel"
              [initialLabel]="contactPersonLabel()" />
          <button mat-icon-button type="button" matTooltip="Add new person"
                  (click)="addPerson('contact')">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Areas</mat-label>
          <mat-select formControlName="areaIds" multiple>
            @for (a of areas(); track a.id) {
              <mat-option [value]="a.id">{{ a.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <div class="form-row add-area-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>New area name</mat-label>
            <input matInput [(ngModel)]="newAreaName" [ngModelOptions]="{standalone: true}"
                   (keydown.enter)="onAddAreaKeydown($event)" />
          </mat-form-field>
          <button mat-stroked-button type="button"
                  [disabled]="!newAreaName.trim() || addingArea()"
                  (click)="addArea()">
            Add Area
          </button>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Onboard Date</mat-label>
            <input matInput type="date" formControlName="onboardDate" />
            @if (form.controls.onboardDate.invalid && form.controls.onboardDate.touched) {
              <mat-error>Onboard date is required</mat-error>
            }
          </mat-form-field>
          <mat-checkbox formControlName="isActive">Active</mat-checkbox>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Skills</mat-label>
          <textarea matInput formControlName="skills" rows="2"></textarea>
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Availability</mat-label>
          <input matInput formControlName="availability" placeholder="e.g. Weekends, evenings" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Notes</mat-label>
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
          {{ isEdit ? 'Save Changes' : 'Create' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .volunteer-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 420px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; align-items: flex-start; }
    .flex-1 { flex: 1; }
    .add-area-row { margin-top: -8px; margin-bottom: 8px; align-items: center; }
  `],
})
export class VolunteerFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private personService = inject(PersonService);
  private volunteerService = inject(VolunteerService);
  private areaService = inject(VolunteerAreaService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<VolunteerFormComponent>);
  private notifications = inject(NotificationService);

  data = inject<VolunteerFormData>(MAT_DIALOG_DATA);
  entity = this.data.entity;

  isEdit = !!this.entity;
  saving = signal(false);
  addingArea = signal(false);
  newAreaName = '';

  areas = signal<VolunteerArea[]>([]);
  volunteerLabel = signal('');
  contactPersonLabel = signal('');

  personLabel = personLabel;
  searchByFirstName = (q: string) => this.personService.search('firstName', q);

  form = this.fb.nonNullable.group({
    personId: ['', Validators.required],
    contactPersonId: this.fb.control<string | null>(null),
    areaIds: this.fb.nonNullable.control<string[]>([]),
    onboardDate: ['', Validators.required],
    isActive: [true],
    skills: [''],
    availability: [''],
    notes: [''],
  });

  ngOnInit(): void {
    this.areaService.getPage({ page: 0, size: 100 }).subscribe(page => {
      this.areas.set(page.content);
    });

    if (this.entity) {
      this.form.patchValue({
        personId: this.entity.person.id,
        contactPersonId: this.entity.contactPerson?.id ?? null,
        areaIds: (this.entity.areas ?? []).map(a => a.id),
        onboardDate: this.entity.onboardDate,
        isActive: this.entity.isActive ?? true,
        skills: this.entity.skills ?? '',
        availability: this.entity.availability ?? '',
        notes: this.entity.notes ?? '',
      });
      this.volunteerLabel.set(personLabel(this.entity.person));
      if (this.entity.contactPerson) {
        this.contactPersonLabel.set(personLabel(this.entity.contactPerson));
      }
    }
  }

  addPerson(target: 'volunteer' | 'contact'): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((created: Person | undefined) => {
        if (!created) return;
        if (target === 'volunteer') {
          this.form.controls.personId.setValue(created.id);
          this.volunteerLabel.set(personLabel(created));
        } else {
          this.form.controls.contactPersonId.setValue(created.id);
          this.contactPersonLabel.set(personLabel(created));
        }
      });
  }

  onAddAreaKeydown(event: Event): void {
    event.preventDefault();
    this.addArea();
  }

  addArea(): void {
    const name = this.newAreaName.trim();
    if (!name) return;
    this.addingArea.set(true);
    this.areaService
      .create({ name })
      .subscribe({
        next: created => {
          this.areas.update(list => [...list, created]);
          this.form.controls.areaIds.setValue([...this.form.controls.areaIds.value, created.id]);
          this.newAreaName = '';
          this.addingArea.set(false);
        },
        error: () => this.addingArea.set(false),
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const raw = this.form.getRawValue();
    // Backend only reads .getId() off nested relations on write, never the full object.
    const payload: Partial<Volunteer> = {
      person: { id: raw.personId } as Person,
      contactPerson: raw.contactPersonId ? ({ id: raw.contactPersonId } as Person) : undefined,
      onboardDate: raw.onboardDate,
      isActive: raw.isActive,
      skills: raw.skills,
      availability: raw.availability,
      notes: raw.notes,
      areas: raw.areaIds.map(id => ({ id }) as VolunteerArea),
    };

    const op = this.isEdit
      ? this.volunteerService.update(this.entity!.id, payload)
      : this.volunteerService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Volunteer ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
