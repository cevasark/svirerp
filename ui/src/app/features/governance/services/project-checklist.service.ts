import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ENVIRONMENT } from '../../../core/tokens/environment.token';
import { ProjectChecklist, ProjectChecklistItem } from '../../../core/models/domain.model';

/** Not a ResourceService subclass — a checklist's own CRUD lives under two different URL bases
 *  (create/list under the owning project, update/delete under its own id), so the base class's
 *  single-`resourcePath` assumption doesn't fit cleanly here. */
@Injectable({ providedIn: 'root' })
export class ProjectChecklistService {
  private readonly http = inject(HttpClient);
  private readonly env = inject(ENVIRONMENT);

  /** Deliberately unpaginated — a project's checklists are meant to be seen as one whole list,
   *  same idiom as ProjectTaskService#getForProject. */
  getForProject(projectId: string): Observable<ProjectChecklist[]> {
    return this.http.get<ProjectChecklist[]>(`${this.env.apiUrl}/projects/${projectId}/checklists`);
  }

  create(projectId: string, body: { title: string; completionDate?: string | null }): Observable<ProjectChecklist> {
    return this.http.post<ProjectChecklist>(`${this.env.apiUrl}/projects/${projectId}/checklists`, body);
  }

  update(checklistId: string, body: { title: string; completionDate?: string | null }): Observable<ProjectChecklist> {
    return this.http.put<ProjectChecklist>(`${this.env.apiUrl}/project-checklists/${checklistId}`, body);
  }

  remove(checklistId: string): Observable<void> {
    return this.http.delete<void>(`${this.env.apiUrl}/project-checklists/${checklistId}`);
  }

  getItems(checklistId: string): Observable<ProjectChecklistItem[]> {
    return this.http.get<ProjectChecklistItem[]>(`${this.env.apiUrl}/project-checklists/${checklistId}/items`);
  }

  addItem(checklistId: string, text: string): Observable<ProjectChecklistItem> {
    return this.http.post<ProjectChecklistItem>(`${this.env.apiUrl}/project-checklists/${checklistId}/items`, { text });
  }

  removeItem(itemId: string): Observable<void> {
    return this.http.delete<void>(`${this.env.apiUrl}/project-checklist-items/${itemId}`);
  }

  markDone(itemId: string, detail?: string | null): Observable<ProjectChecklistItem> {
    return this.http.post<ProjectChecklistItem>(`${this.env.apiUrl}/project-checklist-items/${itemId}/done`, { detail: detail || null });
  }

  markSkipped(itemId: string, detail?: string | null): Observable<ProjectChecklistItem> {
    return this.http.post<ProjectChecklistItem>(`${this.env.apiUrl}/project-checklist-items/${itemId}/skip`, { detail: detail || null });
  }

  reopen(itemId: string): Observable<ProjectChecklistItem> {
    return this.http.post<ProjectChecklistItem>(`${this.env.apiUrl}/project-checklist-items/${itemId}/reopen`, {});
  }
}
