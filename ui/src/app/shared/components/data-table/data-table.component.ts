import { Component, inject, input, output, computed, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule, Sort, SortDirection } from '@angular/material/sort';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

export interface TableColumn {
  key: string;
  header: string;
  /** Optional transform applied when rendering the cell value. */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  cell?: (row: any) => string;
  /** If set, the cell renders as a clickable link that calls this instead of plain text. */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  link?: (row: any) => void;
  /** Makes the header clickable to sort — server-side, via the backend's Pageable/Sort, so the
   *  whole dataset is sorted before paging, not just the current page. Clicking resets to page 1.
   *  On mobile (card layout, no header row) this instead surfaces the column in the "Sort by"
   *  dropdown above the cards. */
  sortable?: boolean;
  /** Backend sort property, if it differs from `key` (e.g. a nested path like "person.email", or
   *  because `key` is a computed/display-only column such as a joined name). Defaults to `key`. */
  sortKey?: string;
  /** Sizing hint for the desktop table layout. 'date'/'number'/'status'/'boolean' values are
   *  naturally short and are pinned to a narrow fixed width instead of eating space that's better
   *  spent elsewhere; 'email' gets a larger share since it's often the longest value in the row.
   *  Defaults to 'text' (a normal flexible share of the remaining width). No effect on the mobile
   *  card layout, which stacks every field at full width regardless. */
  type?: 'text' | 'date' | 'number' | 'status' | 'boolean' | 'email';
}

/** Column width hint per type, for the native <table>'s browser-computed column-grid layout.
 *  'date'/'number'/'status'/'boolean' get a fixed width so they never claim more than they need;
 *  'text'/'email' get a min-width floor and are free to grow into whatever space is left — 'email'
 *  starts from a taller floor since it's typically the longest value in the row. Unlike the old
 *  flex-based sizing, a real <table> guarantees these resolve to the SAME pixel width in the
 *  header row and every body row — a flex mat-table lays out each row as an independent flex
 *  container, so header/body column widths could silently drift apart when row content differed
 *  (e.g. a short "Email" header next to a long real address) even with identical flex values. */
const COLUMN_WIDTH: Record<NonNullable<TableColumn['type']>, string> = {
  date: '130px',
  number: '100px',
  status: '120px',
  boolean: '90px',
  email: '220px',
  text: '140px',
};
/** Fixed-width types must not wrap — a wrapped header inside Angular Material's mat-sort-header
 *  renders its second line centered instead of left-aligned (a known Material quirk), and with
 *  only 90-130px to work with, a long enough locale/value could otherwise trigger it. */
const COLUMN_NOWRAP: Record<NonNullable<TableColumn['type']>, boolean> = {
  date: true,
  number: true,
  status: true,
  boolean: true,
  email: false,
  text: false,
};

export interface TableAction {
  icon: string;
  label: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  action: (row: any) => void;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  disabled?: (row: any) => boolean;
}

/**
 * Generic paginated data table backed by Angular Material. Below the Handset breakpoint, renders
 * each row as a stacked card (label: value pairs) instead of a wide table row — a fixed-column
 * mat-table just crushes illegibly on a phone rather than reflowing. Column-header sort clicks
 * have no equivalent in card layout (no header row), so a compact "Sort by" control takes over
 * there; both paths emit through the same (sortChange)/(pageChange) outputs, so callers don't
 * need to know which layout is active.
 *
 * Usage:
 *   <app-data-table
 *     [columns]="columns"
 *     [actions]="actions"
 *     [data]="page()"
 *     [loading]="loading()"
 *     [pageParams]="pageParams()"
 *     (pageChange)="onPage($event)" />
 */
@Component({
  selector: 'app-data-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatProgressBarModule,
    MatIconModule,
    MatButtonModule,
    MatTooltipModule,
    MatCardModule,
    MatFormFieldModule,
    MatSelectModule,
  ],
  template: `
    <div class="table-wrapper">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" class="loading-bar" />
      }

      @if (isMobile()) {
        @if (sortableColumns().length) {
          <div class="mobile-sort-bar">
            <mat-form-field appearance="outline" class="mobile-sort-field">
              <mat-label>Sort by</mat-label>
              <mat-select [value]="activeSortKey() || null" (selectionChange)="onMobileSortKeyChange($event.value)">
                @for (col of sortableColumns(); track col.key) {
                  <mat-option [value]="col.sortKey ?? col.key">{{ col.header }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button mat-icon-button [disabled]="!activeSortKey()"
                    [matTooltip]="activeSortDirection() === 'desc' ? 'Descending — tap for ascending' : 'Ascending — tap for descending'"
                    (click)="toggleMobileSortDirection()">
              <mat-icon>{{ activeSortDirection() === 'desc' ? 'arrow_downward' : 'arrow_upward' }}</mat-icon>
            </button>
          </div>
        }

        <div class="card-list">
          @if (!rows().length) {
            <p class="no-data-cell">{{ emptyMessage() }}</p>
          }
          @for (row of rows(); track row.id ?? $index) {
            <mat-card class="data-card">
              <mat-card-content>
                @for (col of columns(); track col.key) {
                  <div class="data-card-field">
                    <span class="field-label">{{ col.header }}</span>
                    <span class="field-value">
                      @if (col.link) {
                        <a class="cell-link" (click)="col.link(row)">{{ col.cell ? col.cell(row) : (row[col.key] ?? '') }}</a>
                      } @else {
                        {{ col.cell ? col.cell(row) : (row[col.key] ?? '') }}
                      }
                    </span>
                  </div>
                }
              </mat-card-content>
              @if (actions().length) {
                <mat-card-actions class="data-card-actions">
                  @for (act of actions(); track act.icon) {
                    <button mat-icon-button
                            [matTooltip]="act.label"
                            [disabled]="act.disabled ? act.disabled(row) : false"
                            (click)="act.action(row)">
                      <mat-icon>{{ act.icon }}</mat-icon>
                    </button>
                  }
                </mat-card-actions>
              }
            </mat-card>
          }
        </div>
      } @else {
        <table mat-table [dataSource]="rows()" matSort matSortDisableClear
               [matSortActive]="activeSortKey()"
               [matSortDirection]="activeSortDirection()"
               (matSortChange)="onSortChange($event)"
               class="data-table mat-elevation-z1">

          @for (col of columns(); track col.key) {
            <ng-container [matColumnDef]="col.key">
              <th mat-header-cell *matHeaderCellDef [style.width]="columnWidth(col.type)"
                  [style.white-space]="columnNoWrap(col.type)"
                  [mat-sort-header]="col.sortKey ?? col.key" [disabled]="!col.sortable">
                {{ col.header }}
              </th>
              <td mat-cell *matCellDef="let row" [style.width]="columnWidth(col.type)"
                  [style.white-space]="columnNoWrap(col.type)">
                @if (col.link) {
                  <a class="cell-link" (click)="col.link(row)">{{ col.cell ? col.cell(row) : (row[col.key] ?? '') }}</a>
                } @else {
                  {{ col.cell ? col.cell(row) : (row[col.key] ?? '') }}
                }
              </td>
            </ng-container>
          }

          <ng-container matColumnDef="_actions">
            <th mat-header-cell *matHeaderCellDef class="actions-cell">Actions</th>
            <td mat-cell *matCellDef="let row" class="actions-cell">
              @for (act of actions(); track act.icon) {
                <button mat-icon-button
                        [matTooltip]="act.label"
                        [disabled]="act.disabled ? act.disabled(row) : false"
                        (click)="act.action(row)">
                  <mat-icon>{{ act.icon }}</mat-icon>
                </button>
              }
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="displayedColumns()"></tr>
          <tr mat-row *matRowDef="let row; columns: displayedColumns()"></tr>

          <tr class="mat-row no-data-row" *matNoDataRow>
            <td [attr.colspan]="displayedColumns().length" class="no-data-cell">
              {{ emptyMessage() }}
            </td>
          </tr>
        </table>
      }

      <mat-paginator
        [length]="data()?.totalElements ?? 0"
        [pageSize]="pageParams().size"
        [pageIndex]="pageParams().page"
        [pageSizeOptions]="[10, 20, 50]"
        showFirstLastButtons
        (page)="pageChange.emit($event)" />
    </div>
  `,
  styles: [`
    .table-wrapper { position: relative; }
    .loading-bar { position: absolute; top: 0; left: 0; right: 0; z-index: 1; }
    .data-table { width: 100%; margin-top: 4px; }
    .actions-cell { text-align: right; white-space: nowrap; width: 1%; min-width: 100px; }
    .no-data-row { display: block; }
    .no-data-cell { text-align: center; padding: 32px; color: rgba(0,0,0,.54); display: block; margin: 0; }
    .cell-link { color: #3f51b5; cursor: pointer; text-decoration: none; }
    .cell-link:hover { text-decoration: underline; }

    .mobile-sort-bar { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
    .mobile-sort-field { flex: 1; }

    .card-list { display: flex; flex-direction: column; gap: 12px; margin-top: 4px; }
    .data-card { width: 100%; box-sizing: border-box; }
    .data-card-field {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 16px;
      padding: 6px 0;
      border-bottom: 1px solid rgba(0,0,0,.06);
    }
    .data-card-field:last-child { border-bottom: none; }
    .field-label { color: rgba(0,0,0,.54); font-size: .8em; flex-shrink: 0; }
    .field-value { text-align: right; word-break: break-word; }
    .data-card-actions { justify-content: flex-end; padding: 4px 8px 8px; }
  `],
})
export class DataTableComponent {
  private breakpoint = inject(BreakpointObserver);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  data = input<Page<any> | null>(null);
  columns = input<TableColumn[]>([]);
  actions = input<TableAction[]>([]);
  loading = input<boolean>(false);
  emptyMessage = input<string>('No records found.');
  pageParams = input<PageParams>(DEFAULT_PAGE_PARAMS);

  pageChange = output<PageEvent>();
  /** Emits the new Spring-Data-style sort string ("field,asc"/"field,desc") to set on
   *  PageParams.sort — the caller is expected to also reset page to 0 and reload. */
  sortChange = output<string | null>();

  isMobile = toSignal(
    this.breakpoint.observe(Breakpoints.Handset).pipe(map(r => r.matches)),
    { initialValue: false },
  );

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  rows = computed<any[]>(() => this.data()?.content ?? []);
  displayedColumns = computed(() => [
    ...this.columns().map(c => c.key),
    '_actions',
  ]);
  sortableColumns = computed(() => this.columns().filter(c => c.sortable));

  columnWidth(type: TableColumn['type']): string {
    return COLUMN_WIDTH[type ?? 'text'];
  }

  columnNoWrap(type: TableColumn['type']): string {
    return COLUMN_NOWRAP[type ?? 'text'] ? 'nowrap' : 'normal';
  }

  activeSortKey = computed(() => this.pageParams().sort?.split(',')[0] ?? '');
  activeSortDirection = computed<SortDirection>(() => (this.pageParams().sort?.split(',')[1] as SortDirection) ?? '');

  onSortChange(sort: Sort): void {
    this.sortChange.emit(sort.direction ? `${sort.active},${sort.direction}` : null);
  }

  onMobileSortKeyChange(key: string): void {
    this.sortChange.emit(`${key},asc`);
  }

  toggleMobileSortDirection(): void {
    const key = this.activeSortKey();
    if (!key) return;
    const nextDirection = this.activeSortDirection() === 'desc' ? 'asc' : 'desc';
    this.sortChange.emit(`${key},${nextDirection}`);
  }
}
