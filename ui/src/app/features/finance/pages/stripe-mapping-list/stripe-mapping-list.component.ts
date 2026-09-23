import { Component, inject, signal, computed, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { StripeIntegrationService } from '../../services/stripe-integration.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { StripeProductMapping } from '../../../../core/models/domain.model';
import { Page } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { StripeProductMappingFormComponent } from '../stripe-product-mapping-form/stripe-product-mapping-form.component';

const PURPOSE_LABELS: Record<string, string> = {
  membership_dues: 'Membership Dues',
  service_request: 'Church Service',
  event_ticket: 'Event Ticket',
  general_income: 'General Income',
};

/** Admin config for the Stripe webhook receiver: which Stripe Price means what. New products
 *  (weddings, baptisms, candles, room rentals, ...) just need a row added here — no code changes —
 *  once they exist as Stripe Prices. See StripeWebhookService for how a mapping is applied. */
@Component({
  selector: 'app-stripe-mapping-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Stripe Product Mappings"
        subtitle="Route each Stripe Price to what a completed payment means — membership dues, a paid church service, an event ticket, or general income"
        actionLabel="Add Mapping"
        actionIcon="add"
        (action)="openForm()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        emptyMessage="No Stripe product mappings yet." />
    </div>
  `,
})
export class StripeMappingListComponent implements OnInit {
  private stripeIntegrationService = inject(StripeIntegrationService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);

  mappings = signal<StripeProductMapping[]>([]);
  loading = signal(false);

  /** This list isn't server-paginated (mapping counts are small — one per product) — synthesized
   *  as a single-page Page<T> so it can reuse the shared DataTableComponent. */
  page = computed<Page<StripeProductMapping>>(() => ({
    content: this.mappings(),
    totalElements: this.mappings().length,
    totalPages: 1,
    size: this.mappings().length || 1,
    number: 0,
  }));

  readonly columns: TableColumn[] = [
    { key: 'displayName', header: 'Name', cell: (m: StripeProductMapping) => m.displayName || '—' },
    { key: 'stripePriceId', header: 'Stripe Price', cell: (m: StripeProductMapping) => m.stripePriceId },
    { key: 'purpose', header: 'Purpose', cell: (m: StripeProductMapping) => PURPOSE_LABELS[m.purpose] ?? m.purpose },
    { key: 'fund', header: 'Fund', cell: (m: StripeProductMapping) => m.fund?.fundName || '—' },
    { key: 'categoryAccount', header: 'Income Account', cell: (m: StripeProductMapping) => m.categoryAccount ? `${m.categoryAccount.accountNumber} — ${m.categoryAccount.accountName}` : 'Default' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Edit', action: (m: StripeProductMapping) => this.openForm(m) },
    { icon: 'delete', label: 'Delete', action: (m: StripeProductMapping) => this.confirmDelete(m) },
  ];

  ngOnInit(): void {
    this.load();
  }

  openForm(mapping?: StripeProductMapping): void {
    this.dialog
      .open(StripeProductMappingFormComponent, { width: '560px', data: { mapping: mapping ?? null } })
      .afterClosed()
      .subscribe(saved => { if (saved) this.load(); });
  }

  confirmDelete(mapping: StripeProductMapping): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Mapping',
          message: `Delete the mapping for "${mapping.displayName || mapping.stripePriceId}"? Future payments against this Price will show as needing a mapping again.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.delete(mapping); });
  }

  private load(): void {
    this.loading.set(true);
    this.stripeIntegrationService.getMappings().subscribe({
      next: mappings => { this.mappings.set(mappings); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  private delete(mapping: StripeProductMapping): void {
    this.stripeIntegrationService.deleteMapping(mapping.id).subscribe({
      next: () => {
        this.notifications.success('Mapping deleted.');
        this.load();
      },
    });
  }
}
