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

import { MemberService } from '../../services/member.service';
import { MembershipTypeService } from '../../services/membership-type.service';
import { PersonService } from '../../../persons/services/person.service';
import { PersonFormComponent } from '../../../persons/pages/person-form/person-form.component';
import { NotificationService } from '../../../../core/services/notification.service';
import { Member, MembershipType, Person } from '../../../../core/models/domain.model';

interface MemberDialogData {
  member: Member | null;
}

const STATUSES = ['active', 'inactive', 'suspended', 'expired', 'pending'] as const;

@Component({
  selector: 'app-member-form',
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
    <h2 mat-dialog-title>{{ isEdit ? 'Edit' : 'Add' }} Member</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="member-form">

        <div class="person-row">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Person</mat-label>
            <mat-select formControlName="personId">
              @for (person of persons(); track person.id) {
                <mat-option [value]="person.id">{{ person.firstName }} {{ person.lastName }} ({{ person.email }})</mat-option>
              }
            </mat-select>
            @if (form.controls.personId.invalid && form.controls.personId.touched) {
              <mat-error>Person is required</mat-error>
            }
          </mat-form-field>

          <button mat-icon-button type="button" matTooltip="Person not in the list? Add a new one"
                  (click)="openNewPersonDialog()">
            <mat-icon>person_add</mat-icon>
          </button>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Membership Type</mat-label>
          <mat-select formControlName="membershipTypeId">
            @for (type of membershipTypes(); track type.id) {
              <mat-option [value]="type.id">{{ type.name }}</mat-option>
            }
          </mat-select>
          @if (form.controls.membershipTypeId.invalid && form.controls.membershipTypeId.touched) {
            <mat-error>Membership type is required</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Member Number</mat-label>
          <input matInput formControlName="memberNumber" />
        </mat-form-field>

        <div class="form-row">
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Join Date</mat-label>
            <input matInput type="date" formControlName="joinDate" />
            @if (form.controls.joinDate.invalid && form.controls.joinDate.touched) {
              <mat-error>Join date is required</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Expiry Date</mat-label>
            <input matInput type="date" formControlName="expiryDate" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Status</mat-label>
          <mat-select formControlName="status">
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ s }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <mat-checkbox formControlName="emailOptIn">Opt in to email communications</mat-checkbox>

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
    .member-form { display: flex; flex-direction: column; gap: 4px; padding-top: 8px; min-width: 400px; }
    .full-width { width: 100%; }
    .form-row { display: flex; gap: 12px; width: 100%; }
    .flex-1 { flex: 1; }
    .person-row { display: flex; align-items: flex-start; gap: 4px; }
    .person-row .full-width { flex: 1; }
  `],
})
export class MemberFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private memberService = inject(MemberService);
  private membershipTypeService = inject(MembershipTypeService);
  private personService = inject(PersonService);
  private dialog = inject(MatDialog);
  private dialogRef = inject(MatDialogRef<MemberFormComponent>);
  private notifications = inject(NotificationService);
  private data = inject<MemberDialogData>(MAT_DIALOG_DATA);

  private member = this.data.member;
  isEdit = !!this.member;
  saving = signal(false);

  readonly statuses = STATUSES;
  persons = signal<Person[]>([]);
  membershipTypes = signal<MembershipType[]>([]);

  private allPersons: Person[] = [];
  private memberedPersonIds = new Set<string>();

  form = this.fb.nonNullable.group({
    personId: ['', Validators.required],
    membershipTypeId: ['', Validators.required],
    memberNumber: [''],
    joinDate: ['', Validators.required],
    expiryDate: [''],
    status: ['active'],
    emailOptIn: [true],
  });

  ngOnInit(): void {
    // No search/autocomplete endpoint exists on the backend yet; a plain
    // dropdown over a reasonably-sized page is fine at this org's scale.
    this.personService.getPage({ page: 0, size: 200 }).subscribe(page => {
      this.allPersons = page.content;
      this.updateAvailablePersons();
    });
    // A person can hold at most one membership per org — exclude anyone who
    // already has one from the picklist (except the person on this very
    // member, when editing, so their own name stays visible/selected).
    this.memberService.getPage({ page: 0, size: 500 }).subscribe(page => {
      this.memberedPersonIds = new Set(page.content.map(m => m.person.id));
      this.updateAvailablePersons();
    });
    this.membershipTypeService.getPage({ page: 0, size: 100 })
      .subscribe(page => this.membershipTypes.set(page.content));

    if (this.member) {
      this.form.patchValue({
        personId: this.member.person.id,
        membershipTypeId: this.member.membershipType.id,
        memberNumber: this.member.memberNumber,
        joinDate: this.member.joinDate,
        expiryDate: this.member.expiryDate,
        status: this.member.status,
        emailOptIn: this.member.emailOptIn,
      });
    }
  }

  openNewPersonDialog(): void {
    this.dialog
      .open(PersonFormComponent, { width: '540px', data: null })
      .afterClosed()
      .subscribe((person?: Person) => {
        if (person) {
          this.allPersons = [person, ...this.allPersons];
          this.updateAvailablePersons();
          this.form.patchValue({ personId: person.id });
        }
      });
  }

  private updateAvailablePersons(): void {
    const currentPersonId = this.member?.person.id;
    this.persons.set(
      this.allPersons.filter(p => p.id === currentPersonId || !this.memberedPersonIds.has(p.id)),
    );
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    // The backend only reads `.getId()` off nested person/org/membershipType
    // references, so { id } stubs are all that's needed here.
    const payload = {
      person: { id: value.personId },
      membershipType: { id: value.membershipTypeId },
      memberNumber: value.memberNumber,
      joinDate: value.joinDate,
      expiryDate: value.expiryDate || null,
      status: value.status,
      emailOptIn: value.emailOptIn,
    } as unknown as Partial<Member>;

    const op = this.isEdit
      ? this.memberService.update(this.member!.id, payload)
      : this.memberService.create(payload);

    op.subscribe({
      next: () => {
        this.notifications.success(`Member ${this.isEdit ? 'updated' : 'created'}.`);
        this.dialogRef.close(true);
      },
      error: () => this.saving.set(false),
    });
  }
}
