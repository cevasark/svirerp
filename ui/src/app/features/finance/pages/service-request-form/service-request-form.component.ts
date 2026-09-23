import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ServiceRequestService } from '../../services/service-request.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { ServiceRequest, Person } from '../../../../core/models/domain.model';

interface ServiceRequestDialogData {
  request: ServiceRequest | null;
}

@Component({
  selector: 'app-service-request-form',
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
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Service Request</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="service-request-form">

        <div class="person-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Requested By</mat-label>
            <mat-select formControlName="requestorPersonId">
              <mat-option [value]="null">— none —</mat-option>
              @for (person of persons(); track person.id) {
                <mat-option [value]="person.id">{{ person.firstName }} {{ person.lastName }} ({{ person.email }})</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <button mat-icon-button type="button" matTooltip="Person not in the list? Add a new one"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Service Type</mat-label>
            <mat-select formControlName="serviceType">
              <mat-option value="wedding">Wedding</mat-option>
              <mat-option value="baptism">Baptism</mat-option>
              <mat-option value="funeral">Funeral</mat-option>
              <mat-option value="memorial">Memorial</mat-option>
              <mat-option value="blessing">Blessing</mat-option>
              <mat-option value="other">Other</mat-option>
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Status</mat-label>
            <mat-select formControlName="status">
              <mat-option value="requested">Requested</mat-option>
              <mat-option value="scheduled">Scheduled</mat-option>
              <mat-option value="completed">Completed</mat-option>
              <mat-option value="cancelled">Cancelled</mat-option>
            </mat-select>
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Requested Date</mat-label>
            <input matInput type="date" formControlName="requestedDate" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Agreed Amount</mat-label>
            <input matInput type="number" step="0.01" formControlName="agreedAmount" />
          </mat-form-field>
        </div>

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
    .service-request-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 440px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class ServiceRequestFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private serviceRequestService = inject(ServiceRequestService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<ServiceRequestFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<ServiceRequestDialogData>(MAT_DIALOG_DATA);

  private request = this.data.request;
  isEdit = !!this.request;
  saving = signal(false);

  persons = signal<Person[]>([]);

  form = this.fb.nonNullable.group({
    requestorPersonId: this.fb.control<string | null>(null),
    serviceType: ['wedding', Validators.required],
    status: ['requested', Validators.required],
    requestedDate: [''],
    agreedAmount: [0],
    notes: [''],
  });

  ngOnInit(): void {
    // No search/autocomplete endpoint exists on the backend yet; a plain
    // dropdown over a reasonably-sized page is fine at this org's scale.
    this.personService.getPage({ page: 0, size: 200 }).subscribe(page => {
      this.persons.set(page.content);
    });

    if (this.request) {
      this.form.patchValue({
        requestorPersonId: this.request.requestorPerson?.id ?? null,
        serviceType: this.request.serviceType,
        status: this.request.status,
        requestedDate: this.request.requestedDate,
        agreedAmount: this.request.agreedAmount,
        notes: this.request.notes,
      });
    }
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.persons.set([person, ...this.persons()]);
          this.form.patchValue({ requestorPersonId: person.id });
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
    const payload = {
      requestorPerson: value.requestorPersonId ? { id: value.requestorPersonId } : null,
      serviceType: value.serviceType,
      status: value.status,
      requestedDate: value.requestedDate || null,
      agreedAmount: value.agreedAmount,
      notes: value.notes || null,
    } as unknown as Partial<ServiceRequest>;

    const op = this.isEdit
      ? this.serviceRequestService.update(this.request!.id, payload)
      : this.serviceRequestService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Service request ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
