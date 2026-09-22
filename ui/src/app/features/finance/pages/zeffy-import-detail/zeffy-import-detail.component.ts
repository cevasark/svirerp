import { Component, inject, signal, computed, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { MatCheckboxChange, MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ZeffyImportService } from '../../services/zeffy-import.service';
import { FundService } from '../../services/fund.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ZeffyImportBatch, ZeffyImportRow, Fund } from '../../../../core/models/domain.model';
import { ZeffyImportSummary, ZeffyImportCommitResult, ZeffyCampaignMappingRequest } from '../../../../core/models/api.model';

interface MappingSelection {
  fundId: string | null;
  isMembershipPayment: boolean;
}

const OUTCOME_LABELS: Record<ZeffyImportRow['outcome'], string> = {
  pending_preview: 'Pending',
  ready: 'Ready',
  duplicate: 'Duplicate — already imported',
  skipped_status: 'Skipped (not succeeded)',
  unmapped_campaign: 'Needs fund mapping',
  error: 'Error',
  committed: 'Committed',
};

@Component({
  selector: 'app-zeffy-import-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatCheckboxModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <div class="page-container">
      <a routerLink="/finance/zeffy-import" class="back-link">
        <mat-icon>arrow_back</mat-icon> Back to Zeffy Import
      </a>

      @if (batch(); as b) {
        <h1 class="mat-headline-5">{{ b.fileName }}</h1>
        <p class="mat-body-2 subtitle">
          Uploaded {{ b.uploadedAt ? formatDate(b.uploadedAt) : '—' }} ·
          {{ b.status === 'committed' ? 'Committed' : 'Preview only — not yet committed' }}
        </p>
      }

      @if (summary(); as s) {
        <div class="summary-cards">
          <div class="summary-card"><span class="value">{{ s.readyCount }}</span><span class="label">Ready</span></div>
          <div class="summary-card"><span class="value">{{ s.duplicateCount }}</span><span class="label">Duplicates</span></div>
          <div class="summary-card"><span class="value">{{ s.skippedStatusCount }}</span><span class="label">Skipped (status)</span></div>
          <div class="summary-card"><span class="value">{{ s.unmappedCampaignCount }}</span><span class="label">Needs fund mapping</span></div>
          <div class="summary-card"><span class="value">{{ s.errorCount }}</span><span class="label">Errors</span></div>
          <div class="summary-card"><span class="value">{{ s.committedCount }}</span><span class="label">Committed</span></div>
          <div class="summary-card"><span class="value">{{ s.newPersonCount }}</span><span class="label">New people</span></div>
          <div class="summary-card"><span class="value">{{ s.newMemberCount }}</span><span class="label">New members</span></div>
          <div class="summary-card"><span class="value">{{ formatCurrency(s.totalAmountReady) }}</span><span class="label">Total (ready)</span></div>
        </div>

        @if (s.unmappedCampaignTitles.length && batch()?.status !== 'committed') {
          <div class="mapping-panel">
            <h2 class="mat-headline-6">Assign a fund to each campaign</h2>
            <p class="mat-body-2">
              These Campaign Title values don't have a fund mapped yet — rows for them won't be
              committed until you assign one. The mapping is remembered for future imports.
            </p>
            @for (title of s.unmappedCampaignTitles; track title) {
              <div class="mapping-row">
                <span class="campaign-title">{{ title }}</span>
                <mat-form-field appearance="outline" class="fund-select">
                  <mat-label>Fund</mat-label>
                  <mat-select [value]="mappingSelections()[title]?.fundId ?? null"
                              (selectionChange)="onMappingSelect(title, $event)">
                    @for (f of funds(); track f.id) {
                      <mat-option [value]="f.id">{{ f.fundName }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-checkbox [checked]="mappingSelections()[title]?.isMembershipPayment ?? false"
                              (change)="onMembershipToggle(title, $event)"
                              matTooltip="Zeffy sometimes implements membership registration as a Ticket-type
                                          product instead of a Donation — check this if that's what this
                                          campaign actually is, so it still counts toward membership.">
                  Membership payment
                </mat-checkbox>
              </div>
            }
            <button mat-stroked-button color="primary" [disabled]="!allMappingsSelected() || savingMappings()"
                    (click)="saveMappings()">
              @if (savingMappings()) {
                <mat-progress-spinner diameter="18" mode="indeterminate" />
              } @else {
                Save Fund Mappings
              }
            </button>
          </div>
        }
      }

      @if (batch()?.status !== 'committed') {
        <div class="commit-bar">
          <button mat-flat-button color="primary" [disabled]="!canCommit() || committing()" (click)="commit()">
            @if (committing()) {
              <mat-progress-spinner diameter="20" mode="indeterminate" />
            } @else {
              Commit Import
            }
          </button>
          @if (!canCommit() && summary()?.unmappedCampaignCount) {
            <span class="commit-hint">Assign every campaign's fund above before committing.</span>
          }
        </div>
      }

      @if (commitResult(); as r) {
        <p class="commit-result">
          Committed {{ r.committed }} row(s), {{ r.failed }} failed, {{ r.stillUnmappedCampaign }} still need a fund.
        </p>
      }

      <h2 class="mat-headline-6">Rows</h2>
      <table class="rows-table">
        <thead>
          <tr>
            <th>#</th><th>Name</th><th>Email</th><th>Amount</th><th>Category</th><th>Campaign</th><th>Outcome</th><th>Detail</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row.id) {
            <tr>
              <td>{{ row.rowNumber }}</td>
              <td>{{ row.firstName }} {{ row.lastName }}</td>
              <td>{{ row.email }}</td>
              <td>{{ row.amount != null ? formatCurrency(row.amount) : '—' }}</td>
              <td>{{ row.category || '—' }}</td>
              <td>{{ row.campaignTitle || '—' }}</td>
              <td>{{ outcomeLabel(row) }}</td>
              <td>{{ row.outcomeDetail || '—' }}</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .back-link { display: inline-flex; align-items: center; gap: 4px; margin-bottom: 12px; color: rgba(0,0,0,.6); text-decoration: none; }
    .subtitle { color: rgba(0,0,0,.54); margin-top: 0; }
    .summary-cards { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
    .summary-card {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      padding: 12px 16px; border-radius: 8px; background: rgba(0,0,0,.04); min-width: 110px;
    }
    .summary-card .value { font-size: 1.4em; font-weight: 600; }
    .summary-card .label { font-size: .8em; color: rgba(0,0,0,.54); }
    .mapping-panel { margin: 16px 0; padding: 16px; border-radius: 8px; background: rgba(0,0,0,.03); }
    .mapping-row { display: flex; align-items: center; gap: 16px; margin-bottom: 4px; }
    .campaign-title { min-width: 220px; font-weight: 500; }
    .fund-select { width: 280px; }
    .commit-bar { display: flex; align-items: center; gap: 12px; margin: 16px 0; }
    .commit-hint { color: rgba(0,0,0,.54); font-size: .9em; }
    .commit-result { font-weight: 500; }
    .rows-table { width: 100%; border-collapse: collapse; font-size: .9em; margin-top: 8px; }
    .rows-table th, .rows-table td { text-align: left; padding: 6px 10px; border-bottom: 1px solid rgba(0,0,0,.08); }
  `],
})
export class ZeffyImportDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private zeffyImportService = inject(ZeffyImportService);
  private fundService = inject(FundService);
  private notifications = inject(NotificationService);

  private batchId = this.route.snapshot.paramMap.get('batchId')!;
  private orgId = computed(() => this.batch()?.org.id ?? null);

  batch = signal<ZeffyImportBatch | null>(null);
  summary = signal<ZeffyImportSummary | null>(null);
  rows = signal<ZeffyImportRow[]>([]);
  funds = signal<Fund[]>([]);
  commitResult = signal<ZeffyImportCommitResult | null>(null);
  savingMappings = signal(false);
  committing = signal(false);

  // A plain mutation of this object would never be seen by the `computed()`s below — computed()
  // only re-evaluates when a *signal* it read changes, so this has to be a signal itself, updated
  // immutably via .update(), not a plain object driven by [(ngModel)].
  mappingSelections = signal<Record<string, MappingSelection>>({});

  allMappingsSelected = computed(() => {
    const s = this.summary();
    if (!s) return false;
    const selections = this.mappingSelections();
    return s.unmappedCampaignTitles.every(title => !!selections[title]?.fundId);
  });

  // unmappedCampaignCount reflects each row's outcome as stored on the backend, which only
  // changes at actual commit time — saving a fund mapping does NOT retroactively flip it, so
  // gating solely on "=== 0" would make Commit permanently unreachable whenever a campaign was
  // ever unmapped. allMappingsSelected() is the real signal that everything is now resolvable —
  // commit() itself persists any pending selections before calling the commit endpoint.
  canCommit = computed(() => {
    const s = this.summary();
    if (!s) return false;
    const hasCommittableRows = s.readyCount > 0 || s.unmappedCampaignCount > 0;
    return hasCommittableRows && this.allMappingsSelected();
  });

  ngOnInit(): void {
    this.load();
  }

  outcomeLabel(row: ZeffyImportRow): string {
    return OUTCOME_LABELS[row.outcome];
  }

  formatCurrency(amount: number): string {
    return amount.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
  }

  formatDate(iso: string): string {
    return new Date(iso).toLocaleString();
  }

  onMappingSelect(title: string, event: MatSelectChange): void {
    this.mappingSelections.update(current => ({
      ...current,
      [title]: { ...this.selectionFor(current, title), fundId: event.value },
    }));
  }

  onMembershipToggle(title: string, event: MatCheckboxChange): void {
    this.mappingSelections.update(current => ({
      ...current,
      [title]: { ...this.selectionFor(current, title), isMembershipPayment: event.checked },
    }));
  }

  private selectionFor(current: Record<string, MappingSelection>, title: string): MappingSelection {
    return current[title] ?? { fundId: null, isMembershipPayment: false };
  }

  saveMappings(): void {
    const orgId = this.orgId();
    if (!orgId) return;
    const requests = this.pendingMappingRequests();
    if (!requests.length) return;

    this.savingMappings.set(true);
    this.zeffyImportService.upsertMappings(orgId, requests).subscribe({
      next: () => {
        this.savingMappings.set(false);
        this.notifications.success('Fund mappings saved.');
        this.loadSummaryAndRows();
      },
      error: () => {
        this.savingMappings.set(false);
        this.notifications.error('Could not save fund mappings.');
      },
    });
  }

  commit(): void {
    const orgId = this.orgId();
    if (!orgId) return;
    this.committing.set(true);

    const requests = this.pendingMappingRequests();
    if (requests.length) {
      // Commit is reachable as soon as every unmapped campaign has a selection, whether or not
      // "Save Fund Mappings" was clicked separately — persist any pending ones first so the
      // backend's commit-time mapping lookup actually finds them.
      this.zeffyImportService.upsertMappings(orgId, requests).subscribe({
        next: () => this.doCommit(orgId),
        error: () => {
          this.committing.set(false);
          this.notifications.error('Could not save fund mappings.');
        },
      });
    } else {
      this.doCommit(orgId);
    }
  }

  private doCommit(orgId: string): void {
    this.zeffyImportService.commit(orgId, this.batchId).subscribe({
      next: result => {
        this.committing.set(false);
        this.commitResult.set(result);
        this.notifications.success(`Import committed: ${result.committed} row(s) applied.`);
        this.load();
      },
      error: () => {
        this.committing.set(false);
        this.notifications.error('Commit failed.');
      },
    });
  }

  private pendingMappingRequests(): ZeffyCampaignMappingRequest[] {
    return Object.entries(this.mappingSelections())
      .filter((entry): entry is [string, MappingSelection] => !!entry[1]?.fundId)
      .map(([campaignTitle, sel]) => ({
        campaignTitle,
        fundId: sel.fundId!,
        isMembershipPayment: sel.isMembershipPayment,
      }));
  }

  private load(): void {
    this.zeffyImportService.getBatch(this.batchId).subscribe(batch => {
      this.batch.set(batch);
      this.fundService.getPageForOrg(batch.org.id, { page: 0, size: 200 }).subscribe(page => {
        this.funds.set(page.content);
      });
    });
    this.loadSummaryAndRows();
  }

  private loadSummaryAndRows(): void {
    this.zeffyImportService.getSummary(this.batchId).subscribe(summary => {
      this.summary.set(summary);
    });
    this.zeffyImportService.getRows(this.batchId, { page: 0, size: 200 }).subscribe(page => {
      this.rows.set(page.content);
    });
  }
}
