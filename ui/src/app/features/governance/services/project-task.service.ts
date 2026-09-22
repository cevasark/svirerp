import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ResourceService } from '../../../core/services/resource.service';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { ProjectTask, ProjectTaskComment } from '../../../core/models/domain.model';

@Injectable({ providedIn: 'root' })
export class ProjectTaskService extends ResourceService<ProjectTask> {
  private readonly orgScopedEnv = inject(ENVIRONMENT);

  constructor() {
    super('project-tasks');
  }

  /** Deliberately unpaginated — a project's tasks are meant to be seen as one whole list on the
   *  project's detail page, not paged through (same idiom as ActionItemService#getForMeeting). */
  getForProject(projectId: string): Observable<ProjectTask[]> {
    return this.http.get<ProjectTask[]>(`${this.orgScopedEnv.apiUrl}/projects/${projectId}/tasks`);
  }

  getComments(taskId: string): Observable<ProjectTaskComment[]> {
    return this.http.get<ProjectTaskComment[]>(this.endpoint(`/${taskId}/comments`));
  }

  /** authorName is stamped server-side from the logged-in session — see ProjectService#addComment. */
  addComment(taskId: string, comment: string): Observable<ProjectTaskComment> {
    return this.http.post<ProjectTaskComment>(this.endpoint(`/${taskId}/comments`), { comment });
  }

  removeComment(commentId: string): Observable<void> {
    return this.http.delete<void>(`${this.orgScopedEnv.apiUrl}/project-task-comments/${commentId}`);
  }
}
