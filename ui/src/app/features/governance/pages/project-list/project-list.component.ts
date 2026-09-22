import { Component, inject, signal, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';

import { ProjectService } from '../../services/project.service';
import { OrgContextService } from '../../../../core/services/org-context.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { Project } from '../../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../../core/models/api.model';
import { DataTableComponent, TableColumn, TableAction } from '../../../../shared/components/data-table/data-table.component';
import { PageHeaderComponent } from '../../../../shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { ProjectFormComponent } from '../project-form/project-form.component';

const STATUSES = ['planning', 'in_progress', 'on_hold', 'completed', 'cancelled'] as const;
const STATUS_LABELS: Record<string, string> = {
  planning: 'Planning',
  in_progress: 'In Progress',
  on_hold: 'On Hold',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

@Component({
  selector: 'app-project-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataTableComponent, PageHeaderComponent, MatFormFieldModule, MatSelectModule, FormsModule],
  template: `
    <div class="page-container">
      <app-page-header
        title="Projects"
        subtitle="Task-tracking projects"
        actionLabel="Add Project"
        actionIcon="add_task"
        (action)="openForm()" />

      <div class="filter-bar">
        <mat-form-field appearance="outline" class="filter-field">
          <mat-label>Status</mat-label>
          <mat-select [(ngModel)]="statusFilter" (selectionChange)="onFilterChange()">
            <mat-option [value]="null">All</mat-option>
            @for (s of statuses; track s) {
              <mat-option [value]="s">{{ statusLabels[s] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </div>

      <app-data-table
        [columns]="columns"
        [actions]="actions"
        [data]="page()"
        [loading]="loading()"
        [pageParams]="pageParams()"
        (pageChange)="onPageChange($event)"
        (sortChange)="onSortChange($event)" />
    </div>
  `,
  styles: [`
    .filter-bar { display: flex; gap: 12px; margin-bottom: 8px; }
    .filter-field { width: 200px; }
  `],
})
export class ProjectListComponent implements OnInit {
  private projectService = inject(ProjectService);
  private orgContext = inject(OrgContextService);
  private dialog = inject(MatDialog);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  private orgId: string | null = null;
  page = signal<Page<Project> | null>(null);
  loading = signal(false);
  // Due Date ascending by default — soonest-due projects surface first.
  pageParams = signal<PageParams>({ ...DEFAULT_PAGE_PARAMS, sort: 'dueDate,asc' });

  readonly statuses = STATUSES;
  readonly statusLabels = STATUS_LABELS;
  statusFilter: string | null = null;

  readonly columns: TableColumn[] = [
    { key: 'name', header: 'Name', link: p => this.openDetail(p) },
    { key: 'assignee', header: 'Assignee', cell: p => p.assignee ? `${p.assignee.firstName} ${p.assignee.lastName}` : 'Unassigned' },
    { key: 'status', header: 'Status', cell: p => STATUS_LABELS[p.status] ?? p.status, type: 'status' },
    { key: 'dueDate', header: 'Due Date', sortable: true, type: 'date' },
  ];

  readonly actions: TableAction[] = [
    { icon: 'open_in_new', label: 'View', action: (p: Project) => this.openDetail(p) },
    { icon: 'edit', label: 'Edit', action: (p: Project) => this.openForm(p) },
    { icon: 'delete', label: 'Delete', action: (p: Project) => this.confirmDelete(p) },
  ];

  ngOnInit(): void {
    this.loadPage();
  }

  onPageChange(event: PageEvent): void {
    this.pageParams.set({ ...this.pageParams(), page: event.pageIndex, size: event.pageSize });
    this.loadPage();
  }

  onFilterChange(): void {
    this.pageParams.set({ ...this.pageParams(), page: 0 });
    this.loadPage();
  }

  onSortChange(sort: string | null): void {
    this.pageParams.set({ ...this.pageParams(), page: 0, sort: sort ?? undefined });
    this.loadPage();
  }

  openDetail(project: Project): void {
    this.router.navigate(['/governance/projects', project.id]);
  }

  openForm(project?: Project): void {
    if (!this.orgId) {
      this.notifications.error('No organization found — create one first, under Organizations.');
      return;
    }
    this.dialog
      .open(ProjectFormComponent, {
        width: '540px',
        data: { orgId: this.orgId, project: project ?? null },
      })
      .afterClosed()
      .subscribe(saved => { if (saved) this.loadPage(); });
  }

  confirmDelete(project: Project): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Delete Project',
          message: `Delete "${project.name}" and all of its tasks/comments? This cannot be undone.`,
          confirmLabel: 'Delete',
        },
      })
      .afterClosed()
      .subscribe(confirmed => { if (confirmed) this.deleteProject(project); });
  }

  private loadPage(): void {
    this.loading.set(true);
    this.orgContext.ensureOrgId().subscribe({
      next: orgId => {
        this.orgId = orgId;
        this.projectService.getPageForOrg(orgId, this.pageParams(), this.statusFilter).subscribe({
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

  private deleteProject(project: Project): void {
    this.projectService.remove(project.id).subscribe({
      next: () => {
        this.notifications.success('Project deleted.');
        this.loadPage();
      },
    });
  }
}
