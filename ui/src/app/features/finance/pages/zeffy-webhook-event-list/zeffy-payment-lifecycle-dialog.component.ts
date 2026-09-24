import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { ZeffyPaymentLifecycle, ZeffyWebhookEvent } from '../../../../core/models/domain.model';

export interface ZeffyPaymentLifecycleDialogData {
  event: ZeffyWebhookEvent;
  lifecycle: ZeffyPaymentLifecycle;
}

@Component({
  selector: 'app-zeffy-payment-lifecycle-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Payment Lifecycle</h2>
    <mat-dialog-content>
      <dl class="summary">
        <dt>Zeffy payment</dt><dd>{{ data.event.zeffyResourceId }}</dd>
        <dt>Event</dt><dd>{{ data.event.eventType }}</dd>
        <dt>Outcome</dt><dd>{{ data.event.processingSummary ?? data.event.errorSummary ?? data.event.status }}</dd>
        @if (data.lifecycle.deletedAt) {
          <dt>Deleted in Zeffy</dt><dd>{{ data.lifecycle.deletedAt | date:'medium' }}</dd>
        }
      </dl>

      <h3>Changes</h3>
      @if (!data.lifecycle.changes.length) { <p class="muted">No lifecycle changes recorded.</p> }
      @for (change of data.lifecycle.changes; track change.id) {
        <section class="item">
          <strong>{{ change.changeKind }}</strong>
          <span>{{ change.observedAt | date:'medium' }}</span>
          <p>{{ change.summary }}</p>
          @if (change.changedFields) { <small>Fields: {{ change.changedFields }}</small> }
        </section>
      }

      <h3>Refunds</h3>
      @if (!data.lifecycle.refunds.length) { <p class="muted">No refunds recorded.</p> }
      @for (refund of data.lifecycle.refunds; track refund.id) {
        <section class="item">
          <strong>{{ refund.currency }} {{ refund.amount | number:'1.2-2' }} — {{ refund.status }}</strong>
          <span>{{ refund.refundCreatedAt | date:'medium' }}</span>
          <p>Correction: {{ refund.correctionStatus }}</p>
          <small>{{ refund.zeffyRefundId }}</small>
        </section>
      }

      <h3>Disputes</h3>
      @if (!data.lifecycle.disputes.length) { <p class="muted">No disputes recorded.</p> }
      @for (dispute of data.lifecycle.disputes; track dispute.id) {
        <section class="item">
          <strong>{{ dispute.currency }} {{ dispute.amount | number:'1.2-2' }} — {{ dispute.status }}</strong>
          <span>{{ dispute.disputeCreatedAt | date:'medium' }}</span>
          <p>{{ dispute.reason ?? 'No reason supplied' }}; correction: {{ dispute.correctionStatus }}</p>
          <small>{{ dispute.zeffyDisputeId }}</small>
        </section>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    mat-dialog-content { min-width: min(680px, 80vw); max-height: 70vh; }
    .summary { display: grid; grid-template-columns: 140px 1fr; gap: 6px 12px; }
    dt { color: rgba(0,0,0,.62); }
    dd { margin: 0; overflow-wrap: anywhere; }
    h3 { margin: 22px 0 8px; }
    .item { border: 1px solid rgba(0,0,0,.12); border-radius: 6px; padding: 10px 12px; margin: 8px 0; }
    .item > span { float: right; color: rgba(0,0,0,.62); }
    .item p { margin: 6px 0; }
    .muted, small { color: rgba(0,0,0,.62); }
  `],
})
export class ZeffyPaymentLifecycleDialogComponent {
  readonly data = inject<ZeffyPaymentLifecycleDialogData>(MAT_DIALOG_DATA);
}
