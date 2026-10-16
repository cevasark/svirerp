import { Component, inject, OnInit, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { RoomRentalService } from '../../../services/room-rental.service';
import { PersonService } from '../../../../persons/services/person.service';
import { TrusteeService } from '../../../../governance/services/trustee.service';
import { NotificationService } from '../../../../../core/services/notification.service';
import { RoomRental, RoomRentalStatus, Person, Trustee } from '../../../../../core/models/domain.model';
import { AutocompleteComponent } from '../../../../../shared/components/autocomplete/autocomplete.component';
import { PersonFormComponent } from '../../../../persons/pages/person-form/person-form.component';

export interface RoomRentalDialogData {
  rental: RoomRental | null;
  defaultDate?: string;
}

const STATUS_OPTIONS: { value: RoomRentalStatus; label: string }[] = [
  { value: 'requested', label: '1. Request Received' },
  { value: 'slot_confirmed', label: '2. Slot Confirmed (Awaiting Deposit)' },
  { value: 'deposit_paid', label: '3. Spot Reserved (Deposit Paid)' },
  { value: 'confirmed', label: '4. Fully Confirmed & Paid' },
  { value: 'completed', label: 'Completed' },
  { value: 'cancelled', label: 'Cancelled' },
];

const EVENT_TYPES = [
  { value: 'reception', label: 'Reception / Party' },
  { value: 'meeting', label: 'Meeting / Assembly' },
  { value: 'celebration', label: 'Celebration / Banquet' },
  { value: 'educational', label: 'Workshop / Class' },
  { value: 'other', label: 'Other' },
];

function personLabel(p: Person): string {
  const parts = [`${p.firstName} ${p.lastName}`];
  if (p.phone) parts.push(`(${p.phone})`);
  else if (p.email) parts.push(`(${p.email})`);
  return parts.join(' ');
}

function toIsoDate(d: Date = new Date()): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-room-rental-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    AutocompleteComponent,
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit Rental Agreement' : 'New Room Rental Request' }}</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="rental-form">

        <!-- Conflict warning banner -->
        @if (conflictWarning()) {
          <div class="conflict-alert">
            <mat-icon class="alert-icon">warning</mat-icon>
            <div>
              <strong>Schedule Conflict Detected!</strong>
              <p class="conflict-desc">{{ conflictWarning() }}</p>
            </div>
          </div>
        }

        <!-- Renter Person Autocomplete -->
        <div class="person-row">
          <app-autocomplete
            class="full-width"
            formControlName="renterPersonId"
            label="Renter (Contact Person) *"
            [searchFn]="searchByFirstName"
            [displayFn]="personLabel"
            [initialLabel]="renterLabel()" />

          <button mat-icon-button type="button" matTooltip="Renter not in directory? Add new person"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-2">
            <mat-label>Event Title</mat-label>
            <input matInput formControlName="eventTitle" placeholder="e.g. Birthday Celebration, Choir Practice" />
            @if (form.controls.eventTitle.invalid && form.controls.eventTitle.touched) {
              <mat-error>Event title is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Event Type</mat-label>
            <mat-select formControlName="eventType">
              @for (t of eventTypes; track t.value) {
                <mat-option [value]="t.value">{{ t.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Room / Facility</mat-label>
            <input matInput formControlName="roomName" placeholder="Parish Hall" (change)="checkConflicts()" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Trustee Contact (Point of Contact)</mat-label>
            <mat-select formControlName="contactTrusteeId">
              <mat-option [value]="null">None assigned</mat-option>
              @for (t of trustees(); track t.id) {
                <mat-option [value]="t.id">{{ t.person.firstName }} {{ t.person.lastName }} ({{ t.role }})</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>

        <!-- Date & Times -->
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-2">
            <mat-label>Event Date</mat-label>
            <input matInput type="date" formControlName="eventDate" (change)="checkConflicts()" />
            @if (form.controls.eventDate.invalid && form.controls.eventDate.touched) {
              <mat-error>Date is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Start Time</mat-label>
            <input matInput type="time" formControlName="startTime" (change)="checkConflicts()" />
            @if (form.controls.startTime.invalid && form.controls.startTime.touched) {
              <mat-error>Start time required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>End Time</mat-label>
            <input matInput type="time" formControlName="endTime" (change)="checkConflicts()" />
            @if (form.controls.endTime.invalid && form.controls.endTime.touched) {
              <mat-error>End time required</mat-error>
            }
          </mat-form-field>
        </div>

        <!-- Financials & Workflow Stage -->
        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Workflow Stage</mat-label>
            <mat-select formControlName="status">
              @for (s of statusOptions; track s.value) {
                <mat-option [value]="s.value">{{ s.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Est. Attendees</mat-label>
            <input matInput type="number" formControlName="estimatedAttendees" min="1" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Total Agreed Fee</mat-label>
            <span matTextPrefix>$&nbsp;</span>
            <input matInput type="number" step="0.01" min="0" formControlName="totalFee" />
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Required Deposit</mat-label>
            <span matTextPrefix>$&nbsp;</span>
            <input matInput type="number" step="0.01" min="0" formControlName="depositAmount" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Notes & Setup Requirements</mat-label>
          <textarea matInput formControlName="notes" rows="2" placeholder="Kitchen access requested, tables arrangement, key pickup notes..."></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="saving()" (click)="save()">
        @if (saving()) {
          <mat-progress-spinner diameter="18" mode="indeterminate" />
        } @else {
          {{ isEdit ? 'Save Changes' : 'Create Rental Request' }}
        }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .rental-form {
      display: flex;
      flex-direction: column;
      gap: 6px;
      padding-top: 6px;
    }
    .form-row {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
    }
    .flex-1 { flex: 1 1 140px; }
    .flex-2 { flex: 2 1 220px; }
    .full-width { width: 100%; }
    .person-row {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .person-row app-autocomplete {
      flex: 1;
    }
    .conflict-alert {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      background: #fff3e0;
      border: 1px solid #ffb74d;
      border-radius: 6px;
      padding: 10px 12px;
      margin-bottom: 8px;
      color: #b78103;
    }
    .alert-icon {
      color: #f57c00;
      font-size: 22px;
      width: 22px;
      height: 22px;
    }
    .conflict-desc {
      margin: 2px 0 0;
      font-size: .85rem;
      color: rgba(0,0,0,.75);
    }
  `],
})
export class RoomRentalFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private rentalService = inject(RoomRentalService);
  private personService = inject(PersonService);
  private trusteeService = inject(TrusteeService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<RoomRentalFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<RoomRentalDialogData>(MAT_DIALOG_DATA);

  rental = this.data?.rental ?? null;
  isEdit = !!this.rental;
  saving = signal(false);
  trustees = signal<Trustee[]>([]);
  conflictWarning = signal<string | null>(null);

  readonly statusOptions = STATUS_OPTIONS;
  readonly eventTypes = EVENT_TYPES;
  renterLabel = signal('');
  personLabel = personLabel;
  searchByFirstName = (q: string) => this.personService.search('firstName', q);

  form = this.fb.nonNullable.group({
    renterPersonId: ['', Validators.required],
    contactTrusteeId: this.fb.control<string | null>(null),
    roomName: ['Parish Hall', Validators.required],
    eventTitle: ['', Validators.required],
    eventType: ['reception'],
    eventDate: [this.data?.defaultDate || toIsoDate(), Validators.required],
    startTime: ['14:00', Validators.required],
    endTime: ['18:00', Validators.required],
    estimatedAttendees: this.fb.control<number | null>(null),
    totalFee: [0, [Validators.required, Validators.min(0)]],
    depositAmount: [0, [Validators.required, Validators.min(0)]],
    status: ['requested' as RoomRentalStatus, Validators.required],
    notes: [''],
  });

  ngOnInit(): void {
    // Load active trustees for the point-of-contact dropdown
    this.trusteeService.getPage({ page: 0, size: 100, sort: 'termStart,desc' }).subscribe({
      next: page => {
        this.trustees.set(page.content.filter(t => t.isActive !== false));
      },
    });

    if (this.rental) {
      const startDt = new Date(this.rental.startTime);
      const endDt = new Date(this.rental.endTime);

      const dateStr = toIsoDate(startDt);
      const startH = String(startDt.getHours()).padStart(2, '0');
      const startM = String(startDt.getMinutes()).padStart(2, '0');
      const endH = String(endDt.getHours()).padStart(2, '0');
      const endM = String(endDt.getMinutes()).padStart(2, '0');

      this.form.patchValue({
        renterPersonId: this.rental.renterPerson.id,
        contactTrusteeId: this.rental.contactTrustee?.id ?? null,
        roomName: this.rental.roomName,
        eventTitle: this.rental.eventTitle,
        eventType: this.rental.eventType || 'reception',
        eventDate: dateStr,
        startTime: `${startH}:${startM}`,
        endTime: `${endH}:${endM}`,
        estimatedAttendees: this.rental.estimatedAttendees ?? null,
        totalFee: this.rental.totalFee,
        depositAmount: this.rental.depositAmount,
        status: this.rental.status,
        notes: this.rental.notes || '',
      });

      this.renterLabel.set(personLabel(this.rental.renterPerson));
    }
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((created?: Person) => {
        if (created) {
          this.form.patchValue({ renterPersonId: created.id });
          this.renterLabel.set(personLabel(created));
        }
      });
  }

  checkConflicts(): void {
    const val = this.form.getRawValue();
    if (!val.eventDate || !val.startTime || !val.endTime || !val.roomName) return;

    const startIso = new Date(`${val.eventDate}T${val.startTime}:00`).toISOString();
    const endIso = new Date(`${val.eventDate}T${val.endTime}:00`).toISOString();

    if (startIso >= endIso) {
      this.conflictWarning.set('Start time must be before end time.');
      return;
    }

    this.rentalService.checkOverlap(startIso, endIso, val.roomName, this.rental?.id).subscribe({
      next: overlaps => {
        if (overlaps.length > 0) {
          const names = overlaps.map(o => `"${o.eventTitle}" (${new Date(o.startTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})} - ${new Date(o.endTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})})`).join(', ');
          this.conflictWarning.set(`Slot overlaps with existing booking(s): ${names}`);
        } else {
          this.conflictWarning.set(null);
        }
      },
      error: () => this.conflictWarning.set(null),
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.getRawValue();
    const startIso = new Date(`${val.eventDate}T${val.startTime}:00`).toISOString();
    const endIso = new Date(`${val.eventDate}T${val.endTime}:00`).toISOString();

    if (startIso >= endIso) {
      this.notifications.error('Start time must be strictly before end time.');
      return;
    }

    this.saving.set(true);

    const payload: Partial<RoomRental> = {
      roomName: val.roomName.trim() || 'Parish Hall',
      eventTitle: val.eventTitle.trim(),
      eventType: val.eventType,
      startTime: startIso,
      endTime: endIso,
      estimatedAttendees: val.estimatedAttendees || undefined,
      totalFee: val.totalFee,
      depositAmount: val.depositAmount,
      status: val.status,
      notes: val.notes.trim() || undefined,
      renterPerson: { id: val.renterPersonId } as any,
      contactTrustee: val.contactTrusteeId ? ({ id: val.contactTrusteeId } as any) : undefined,
    };

    const action$ = this.isEdit && this.rental
      ? this.rentalService.update(this.rental.id, payload)
      : this.rentalService.create(payload);

    action$.subscribe({
      next: saved => {
        this.notifications.success(this.isEdit ? 'Rental updated.' : 'Rental request created.');
        this.dialogRef.close(saved);
      },
      error: () => this.saving.set(false),
    });
  }
}
