import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';

import { ZeffyImportService } from '../../services/zeffy-import.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ZeffyImportBatch } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ZeffyImportUploadDialogComponent } from '../zeffy-import-upload-dialog/zeffy-import-upload-dialog.component';

@Component({
  selector: 'app-zeffy-import-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent],
  template: `
    <div class="page-container">
      <app-page-header
        title="Zeffy Import"
        subtitle="Import a Zeffy Transactions export — preview, resolve any campaign fund mapping, then commit"
        actionLabel="Import Zeffy Transactions"
        actionIcon="upload_file"
        (action)="openUpload()" />

      <app-data-table
        [columns]="columns"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        emptyMessage="No Zeffy imports yet."
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
})
export class ZeffyImportListComponent implements OnInit {
  private zeffyImportService = inject(ZeffyImportService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  private orgId: string | null = null;
  page = signal<Page<ZeffyImportBatch> | null>(null);
  loading = signal(false);
  pageParams = signal<PageParams>(DEFAULT_PAGE_PARAMS);

  readonly columns: TableColumn[] = [
    { key: 'fileName', header: 'File', link: (b: ZeffyImportBatch) => this.openDetail(b) },
    { key: 'uploadedAt', header: 'Uploaded', cell: (b: ZeffyImportBatch) => new Date(b.uploadedAt!).toLocaleString(), sortable: true, type: 'date' },
    { key: 'status', header: 'Status', cell: (b: ZeffyImportBatch) => (b.status === 'committed' ? 'Committed' : 'Preview only'), type: 'status' },
    { key: 'rowCount', header: 'Rows', type: 'number' },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.set({ ...this.pageParams(), page: 0, sort: sort ?? undefined });
    this.loadPage();
  }

  openUpload(): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(ZeffyImportUploadDialogComponent, { width: '540px', data: { orgId: this.orgId } })
      .afterClosed()
      .subscribe((batch: ZeffyImportBatch | null) => {
        if (batch) {
          this.openDetail(batch);
        }
      });
  }

  openDetail(batch: ZeffyImportBatch): void {
    this.router.navigate(['/finance/zeffy-import', batch.id]);
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.zeffyImportService.getBatchesForOrg(orgId, this.pageParams()).subscribe({
          next: data => { this.page.set(data); this.loading.set(false); },
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.loading.set(false);
        this.notifications.error('No organization found — create one first, under Organizations.');
      },
    });
  }
}
