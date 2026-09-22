import { Injectable, inject } from '@angular/core';
import { HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { Project, ProjectComment } from '../../../core/models/domain.model';
import { Page, PageParams, DEFAULT_PAGE_PARAMS } from '../../../core/models/api.model';

@Injectable({ providedIn: 'root' })
export class ProjectService extends ResourceService<Project> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('projects');
  }

  /** List is org-scoped (unlike get/create/update/delete, which are flat). */
  getPageForOrg(orgId: string, params: PageParams = DEFAULT_PAGE_PARAMS, status?: string | null): Observable<Page<Project>> {
    let p = new HttpParams().set('page', String(params.page)).set('size', String(params.size));
    if (params.sort) {
      p = p.set('sort', params.sort);
    }
    if (status) {
      p = p.set('status', status);
    }
    return this.http.get<Page<Project>>(
      `${this.orgScopedEnv.apiUrl}/organizations/${orgId}/projects`,
      { params: p },
    );
  }

  /** Deliberately unpaginated — a project's comment thread is meant to be read in full. */
  getComments(projectId: string): Observable<ProjectComment[]> {
    return this.http.get<ProjectComment[]>(this.endpoint(`/${projectId}/comments`));
  }

  /** authorName is stamped server-side from the logged-in session — the request body only ever
   *  carries the comment text (see GovernanceController#createProjectComment on the backend). */
  addComment(projectId: string, comment: string): Observable<ProjectComment> {
    return this.http.post<ProjectComment>(this.endpoint(`/${projectId}/comments`), { comment });
  }

  removeComment(commentId: string): Observable<void> {
    return this.http.delete<void>(`${this.orgScopedEnv.apiUrl}/project-comments/${commentId}`);
  }
}
