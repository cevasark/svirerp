import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { CurrencyPipe, DatePipe } from '@angular/common';

import { Person, PersonOverview, ZeffyContactSummary } from '../../../../core/models/domain.model';

@Component({
  selector: 'app-person-details-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, CurrencyPipe, DatePipe],
  template: `
    <h2 mat-dialog-title>{{ person.firstName }} {{ person.lastName }}</h2>

    <mat-dialog-content class="details">
      <div class="detail-row">
        <mat-icon>phone</mat-icon>
        <span>{{ person.phone || 'Not provided' }}</span>
      </div>
      <div class="detail-row">
        <mat-icon>email</mat-icon>
        <span>{{ person.email || 'Not provided' }}</span>
      </div>
      @if (person.membershipType) {
        <div class="detail-row">
          <mat-icon>badge</mat-icon>
          <span>
            {{ person.membershipType }} — {{ person.membershipStatus }}
            @if (person.joinDate) { · Joined {{ person.joinDate | date:'mediumDate' }} }
            @if (person.expiryDate) { · Expires {{ person.expiryDate | date:'mediumDate' }} }
          </span>
        </div>
      }

      @if (person.zeffyContacts?.length) {
        <h3>Zeffy</h3>
        @for (contact of person.zeffyContacts; track contact.id) {
          <section class="zeffy-contact">
            <div class="contact-heading">
              <strong>{{ contact.firstName }} {{ contact.lastName }}</strong>
              <span class="status">{{ contact.processingStatus }}</span>
            </div>
            @if (contact.email) { <span>Email: {{ contact.email }}</span> }
            @if (contact.phoneNumber) { <span>Phone: {{ contact.phoneNumber }}</span> }
            @if (contactAddress(contact)) { <span>Address: {{ contactAddress(contact) }}</span> }
            @if (contact.donorType) { <span>Donor type: {{ contact.donorType }}</span> }
            <span>Total contributions:
              {{ contact.totalContribution ?? 0 | currency:(contact.currency ?? 'USD'):'symbol':'1.2-2' }}</span>
            <span>Donation count: {{ contact.donationCount ?? 0 }}</span>
            @if (contact.firstDonationAt) {
              <span>First contribution: {{ contact.firstDonationAt | date:'mediumDate' }}</span>
            }
            @if (contact.lastDonationAt) {
              <span>Last contribution: {{ contact.lastDonationAt | date:'mediumDate' }}</span>
            }
            @if (contact.lastSyncedAt) {
              <span>Last synchronized: {{ contact.lastSyncedAt | date:'medium' }}</span>
            }
            @if (contact.deletedAt) {
              <span>Deleted in Zeffy: {{ contact.deletedAt | date:'medium' }}</span>
            }
            <small>Zeffy ID: {{ contact.zeffyContactId }}</small>
            @if (contact.outcomeReason) { <p>{{ contact.outcomeReason }}</p> }
          </section>
        }
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .details { display: flex; flex-direction: column; gap: 12px; padding-top: 8px; min-width: 280px; }
    .detail-row { display: flex; align-items: center; gap: 12px; }
    .detail-row mat-icon { color: rgba(0,0,0,.54); }
    h3 { margin: 12px 0 0; }
    .zeffy-contact { display: flex; flex-direction: column; gap: 4px; padding: 10px 0; border-top: 1px solid rgba(0,0,0,.12); }
    .contact-heading { display: flex; justify-content: space-between; gap: 16px; }
    .status { font-size: .8rem; font-weight: 600; }
    .zeffy-contact p { margin: 4px 0 0; color: #b3261e; }
  `],
})
export class PersonDetailsDialogComponent {
  person = inject<Person & Partial<PersonOverview>>(MAT_DIALOG_DATA);

  contactAddress(contact: ZeffyContactSummary): string {
    return [contact.addressLine1, contact.city, contact.state, contact.postalCode, contact.country]
      .filter(Boolean).join(', ');
  }
}
