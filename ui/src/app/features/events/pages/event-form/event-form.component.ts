import {
  Component, inject, OnInit,
  signal, ChangeDetectionStrategy,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { EventService } from '../../services/event.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { CalendarEvent, Person } from '../../../../core/models/domain.model';

interface EventDialogData {
  entity: CalendarEvent | null;
}

const STATUSES = ['scheduled', 'cancelled', 'completed', 'postponed'] as const;
const VISIBILITIES = ['public', 'members_only', 'internal'] as const;

@Component({
  selector: 'app-event-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Event</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="event-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Title</mat-label>
          <input matInput formControlName="title" />
          @if (form.controls.title.invalid && form.controls.title.touched) {
            <mat-error>Title is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="2"></textarea>
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Event Type</mat-label>
            <input matInput formControlName="eventType" placeholder="e.g. general, meeting, fundraiser" />
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

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Start</mat-label>
            <input matInput type="datetime-local" formControlName="startDatetime" />
            @if (form.controls.startDatetime.invalid && form.controls.startDatetime.touched) {
              <mat-error>Start is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>End</mat-label>
            <input matInput type="datetime-local" formControlName="endDatetime" />
          </mat-form-field>
        </div>

        <mat-checkbox formControlName="isAllDay">All day</mat-checkbox>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Location</mat-label>
          <input matInput formControlName="location" />
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Virtual Link</mat-label>
          <input matInput formControlName="virtualLink" />
        </mat-form-field>

        <mat-checkbox formControlName="isRecurring">Recurring</mat-checkbox>

        @if (form.controls.isRecurring.value) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Recurrence Rule</mat-label>
            <input matInput formControlName="recurrenceRule" placeholder="e.g. FREQ=WEEKLY;BYDAY=SU" />
          </mat-form-field>
        }

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Visibility</mat-label>
            <mat-select formControlName="visibility">
              @for (v of visibilities; track v) {
                <mat-option [value]="v">{{ v }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Capacity</mat-label>
            <input matInput type="number" min="1" formControlName="capacity" />
          </mat-form-field>
        </div>

        <div class="calendar-row">
          <mat-checkbox formControlName="publishToOfficial">Publish to Official Calendar</mat-checkbox>
          @if (entity?.googleOfficialSyncError) {
            <mat-icon class="sync-warning" [matTooltip]="entity!.googleOfficialSyncError!">warning</mat-icon>
          }
        </div>
        <div class="calendar-row">
          <mat-checkbox formControlName="publishToInternal">Publish to Internal Calendar</mat-checkbox>
          @if (entity?.googleInternalSyncError) {
            <mat-icon class="sync-warning" [matTooltip]="entity!.googleInternalSyncError!">warning</mat-icon>
          }
        </div>

        <div class="person-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Created By (optional)</mat-label>
            <mat-select formControlName="createdById">
              <mat-option [value]="null">None</mat-option>
              @for (person of persons(); track person.id) {
                <mat-option [value]="person.id">{{ person.firstName }} {{ person.lastName }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button mat-icon-button type="button" matTooltip="Add new person"
                  (click)="addPerson()">
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
    .event-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 440px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
    .calendar-row { display: flex; align-items: center; gap: 8px; }
    .sync-warning { color: #f57c00; font-size: 20px; height: 20px; width: 20px; cursor: help; }
  `],
})
export class EventFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private eventService = inject(EventService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<EventFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<EventDialogData>(MAT_DIALOG_DATA);

  entity = this.data.entity;
  isEdit = !!this.entity;
  saving = signal(false);

  readonly statuses = STATUSES;
  readonly visibilities = VISIBILITIES;
  persons = signal<Person[]>([]);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: [''],
    eventType: ['general'],
    status: ['scheduled'],
    startDatetime: ['', Validators.required],
    endDatetime: [''],
    isAllDay: [false],
    location: [''],
    virtualLink: [''],
    isRecurring: [false],
    recurrenceRule: [''],
    visibility: ['public'],
    capacity: this.fb.control<number | null>(null),
    publishToOfficial: [false],
    publishToInternal: [false],
    createdById: this.fb.control<string | null>(null),
  });

  ngOnInit(): void {
    // No search/autocomplete endpoint exists on the backend yet; a plain
    // dropdown over a reasonably-sized page is fine at this org's scale.
    this.personService.getPage({ page: 0, size: 200 }).subscribe(page => this.persons.set(page.content));

    if (this.entity) {
      this.form.patchValue({
        title: this.entity.title,
        description: this.entity.description ?? '',
        eventType: this.entity.eventType ?? 'general',
        status: this.entity.status,
        startDatetime: toLocalInput(this.entity.startDatetime),
        endDatetime: this.entity.endDatetime ? toLocalInput(this.entity.endDatetime) : '',
        isAllDay: this.entity.isAllDay ?? false,
        location: this.entity.location ?? '',
        virtualLink: this.entity.virtualLink ?? '',
        isRecurring: this.entity.isRecurring ?? false,
        recurrenceRule: this.entity.recurrenceRule ?? '',
        visibility: this.entity.visibility,
        capacity: this.entity.capacity ?? null,
        publishToOfficial: this.entity.publishToOfficial ?? false,
        publishToInternal: this.entity.publishToInternal ?? false,
        createdById: this.entity.createdBy?.id ?? null,
      });
    }
  }

  addPerson(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((created: Person | undefined) => {
        if (!created) return;
        this.persons.update(list => [...list, created]);
        this.form.controls.createdById.setValue(created.id);
      });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // Backend only reads .getId() off nested org/createdBy references on write.
    const payload = {
      createdBy: value.createdById ? { id: value.createdById } : null,
      title: value.title,
      description: value.description || null,
      eventType: value.eventType || null,
      status: value.status,
      startDatetime: fromLocalInput(value.startDatetime),
      endDatetime: value.endDatetime ? fromLocalInput(value.endDatetime) : null,
      isAllDay: value.isAllDay,
      location: value.location || null,
      virtualLink: value.virtualLink || null,
      isRecurring: value.isRecurring,
      recurrenceRule: value.isRecurring ? (value.recurrenceRule || null) : null,
      visibility: value.visibility,
      capacity: value.capacity ?? null,
      publishToOfficial: value.publishToOfficial,
      publishToInternal: value.publishToInternal,
    } as unknown as Partial<CalendarEvent>;

    const op = this.isEdit
      ? this.eventService.update(this.entity!.id, payload)
      : this.eventService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Event ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}

/** Backend sends/expects OffsetDateTime ISO strings; <input type="datetime-local"> needs "yyyy-MM-ddTHH:mm". */
function toLocalInput(isoDatetime: string): string {
  const d = new Date(isoDatetime);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function fromLocalInput(localValue: string): string {
  return new Date(localValue).toISOString();
}
