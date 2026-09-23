import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { PageEvent } from '@angular/material/paginator';

import { DEFAULT_PAGE_PARAMS, Page, PageParams } from '../../../../core/models/api.model';
import { ZeffyCampaign } from '../../../../core/models/domain.model';
import { DataTableComponent, TableAction, TableColumn } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ZeffyIntegrationService } from '../../services/zeffy-integration.service';
import { ZeffyCampaignMappingFormComponent } from '../zeffy-campaign-mapping-form/zeffy-campaign-mapping-form.component';

@Component({
  selector: 'app-zeffy-campaign-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Zeffy Campaigns"
        subtitle="Confirm how each API campaign will be handled when payment processing is enabled"
        actionLabel="Refresh"
        actionIcon="refresh"
        (action)="load()" />

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)"
        emptyMessage="No campaigns synchronized. An administrator can sync them under Settings > Zeffy." />
    </div>
  `,
})
export class ZeffyCampaignListComponent implements OnInit {
  private readonly service = inject(ZeffyIntegrationService);
  private readonly dialog = inject(MatDialog);

  readonly pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, sort: 'title,asc' });
  readonly page = signal<Page<ZeffyCampaign> | null>(null);
  readonly loading = signal(false);

  readonly columns: TableColumn[] = [
    { key: 'title', header: 'Campaign', sortable: true, cell: (c: ZeffyCampaign) => c.title },
    { key: 'campaignType', header: 'Type', cell: (c: ZeffyCampaign) => this.typeLabel(c) },
    { key: 'status', header: 'Zeffy Status', type: 'status', cell: (c: ZeffyCampaign) => this.statusLabel(c) },
    { key: 'mapping', header: 'Policy', type: 'status', cell: (c: ZeffyCampaign) => this.policyLabel(c) },
    { key: 'fund', header: 'Fund', cell: (c: ZeffyCampaign) => c.fund?.name ?? '-' },
    { key: 'categoryAccount', header: 'Income Account', cell: (c: ZeffyCampaign) =>
      c.categoryAccount ? `${c.categoryAccount.code} - ${c.categoryAccount.name}` : '-' },
    { key: 'grantsMembershipCredit', header: 'Membership', type: 'boolean',
      cell: (c: ZeffyCampaign) => c.grantsMembershipCredit ? 'Yes' : 'No' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'edit', label: 'Review policy', action: (c: ZeffyCampaign) => this.openMapping(c) },
  ];

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.service.getCampaigns(this.pageParams()).subscribe({
      next: page => { this.page.set(page); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.update(current => ({ ...current, page: event.pageIndex, size: event.pageSize }));
    this.load();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.update(current => ({ ...current, page: 0, sort: sort ?? undefined }));
    this.load();
  }

  openMapping(campaign: ZeffyCampaign): void {
    this.dialog.open(ZeffyCampaignMappingFormComponent, {
      width: '680px',
      maxWidth: '95vw',
      data: { campaign },
    }).afterClosed().subscribe(saved => {
      if (saved) this.load();
    });
  }

  private typeLabel(campaign: ZeffyCampaign): string {
    const type = campaign.campaignType === 'donation_form' ? 'Donation Form' : 'Ticketing';
    return campaign.category ? `${type} / ${campaign.category}` : type;
  }

  private statusLabel(campaign: ZeffyCampaign): string {
    if (campaign.zeffyDeletedAt) return 'Deleted';
    if (campaign.archived) return 'Archived';
    return campaign.status || 'Active';
  }

  private policyLabel(campaign: ZeffyCampaign): string {
    if (!campaign.mappingConfirmed) return `Review (${campaign.suggestedAction})`;
    return campaign.processingAction === 'IGNORE' ? 'Ignored' : 'Apply';
  }
}
