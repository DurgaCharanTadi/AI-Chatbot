import { Injectable, signal } from '@angular/core';
import { Message } from '../models/message';

function uid(){ return Math.random().toString(36).slice(2, 9); }

type ChatRole = 'user'|'assistant';

const BASE_URL = 'https://zf3rjmo5ul.execute-api.us-east-1.amazonaws.com/dev';
// const BASE_URL = 'http://localhost:8080';

@Injectable({ providedIn: 'root' })
export class Chat {
  // keep sticky files across turns
  private stickyFiles: File[] | null = null;
  private threadId = crypto?.randomUUID?.() ?? 't_' + Math.random().toString(36).slice(2);

  messages = signal<Message[]>([
    {
      id: uid(),
      role: 'assistant',
      content: 'Welcome! I\'m your AI assistant. How can I help?',
      timestamp: Date.now()
    }
  ]);

  isStreaming = signal(false); // remains a simple "pending" indicator
  lastUserMessageId: string | null = null;
  controller: AbortController | null = null;

  /**
   * Send a user message. If attachments provided, uses /api/chat/upload/completion (multipart JSON).
   * Otherwise uses /api/chat/completion (JSON).
   */
  async send(content: string, attachments: File[] = []) {
    if (!content?.trim() && (!attachments || attachments.length === 0)) return;

    // remember files for future turns (sticky)
    if (attachments && attachments.length > 0) {
      this.stickyFiles = attachments.slice(); // keep a copy
    }

    const userMsg: Message = {
      id: uid(),
      role: 'user',
      content,
      timestamp: Date.now(),
      attachments: attachments?.map(f => ({ name: f.name, size: f.size, type: f.type }))
    };
    this.messages.update(list => [...list, userMsg]);
    this.lastUserMessageId = userMsg.id;

    // placeholder for assistant response (will set full text once we get JSON)
    const reply: Message = { id: uid(), role: 'assistant', content: '', timestamp: Date.now() };
    this.messages.update(list => [...list, reply]);

    try {
      this.isStreaming.set(true);
      this.controller?.abort(); // cancel any prior in-flight request
      this.controller = new AbortController();

      const filesToUse = (attachments && attachments.length > 0)
        ? attachments
        : (this.stickyFiles ?? []);

      if (filesToUse.length > 0) {
        await this._completionUpload(filesToUse, reply.id);
      } else {
        await this._completion(reply.id);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Request failed.';
      const note = `\n\n⚠️ ${msg}`;
      this._mutateMessage(reply.id, m => m.content += note);
    } finally {
      this.isStreaming.set(false);
      this.controller = null;
    }
  }

  /** Abort the in-flight request (if any). */
  stop() {
    this.controller?.abort();
    this.isStreaming.set(false);
  }

  /**
   * Regenerate the last assistant reply using the same history (remove last assistant then call completion again).
   */
  async regenerate() {
    // Remove last assistant message (if it’s the most recent)
    const list = this.messages();
    for (let i = list.length - 1; i >= 0; i--) {
      if (list[i].role === 'assistant') {
        this.messages.update(arr => arr.filter((_, idx) => idx !== i));
        break;
      }
    }
    // Create a new assistant placeholder and call completion again
    const reply: Message = { id: uid(), role: 'assistant', content: '', timestamp: Date.now() };
    this.messages.update(arr => [...arr, reply]);

    try {
      this.isStreaming.set(true);
      this.controller?.abort();
      this.controller = new AbortController();
      await this._completion(reply.id);
    } catch (err) {
      const note = `\n\n⚠️ ${err instanceof Error ? err.message : 'Request failed.'}`;
      this._mutateMessage(reply.id, m => m.content += note);
    } finally {
      this.isStreaming.set(false);
      this.controller = null;
    }
  }

  /** Clear thread to initial greeting. */
  clear() {
    const oldId = this.threadId;
    this.threadId = crypto?.randomUUID?.() ?? 't_' + Math.random().toString(36).slice(2);

    // tell server to delete memory for the previous thread (best-effort)
    fetch(`${BASE_URL}/api/chat/memory`, {
      method: 'DELETE',
      headers: { 'X-Thread-Id': oldId }
    }).catch(()=>{});

    this.controller?.abort();
    this.messages.set([{
      id: Math.random().toString(36).slice(2, 9),
      role: 'assistant',
      content: 'Welcome! I\'m your AI assistant. How can I help?',
      timestamp: Date.now()
    }]);
    this.isStreaming.set(false);
    this.lastUserMessageId = null;
  }

  // ===== Helpers =====

  /** Build the ChatRequest payload: include the ENTIRE history (user + assistant), mapped to {role, content}. */
  private _buildPayload() {
    const msgs = this.messages()
      .filter(m =>
        (m.role === 'user' || m.role === 'assistant') &&
        m.content != null &&
        m.content.trim().length > 0
      )
      .map(m => ({ role: (m.role as ChatRole), content: m.content }));

    return {
      messages: msgs,
      maxTokens: 768 // tweak as needed (server also has defaults)
    };
  }

  /** Call /api/chat/completion with JSON body (no files). Returns a single JSON with { text }. */
  private async _completion(replyId: string) {
    const payload = this._buildPayload();
    const res = await fetch(`${BASE_URL}/api/chat/completion`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
        'X-Thread-Id': this.threadId
      },
      body: JSON.stringify(payload),
      signal: this.controller!.signal
    });

    let data: any = null;
    try { data = await res.json(); } catch { /* fall through */ }

    if (!res.ok) {
      const msg = data?.message || data?.error || `${res.status} ${res.statusText}`;
      throw new Error(`HTTP ${msg}`);
    }

    const text =
      typeof data?.text === 'string' ? data.text :
      (typeof data === 'string' ? data : JSON.stringify(data));

    this._mutateMessage(replyId, m => m.content = text ?? '');
  }

  /** Call /api/chat/upload/completion with multipart body (files + JSON). Returns single JSON with { text }. */
  private async _completionUpload(files: File[], replyId: string) {
    const form = new FormData();
    for (const f of files) form.append('file', f, f.name);

    const reqBlob = new Blob([JSON.stringify(this._buildPayload())], { type: 'application/json' });
    form.append('request', reqBlob);

    const res = await fetch(`${BASE_URL}/api/chat/upload/completion`, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'X-Thread-Id': this.threadId
      },
      body: form,
      signal: this.controller!.signal
    });

    let data: any = null;
    try { data = await res.json(); } catch { /* fall through */ }

    if (!res.ok) {
      const msg = data?.message || data?.error || `${res.status} ${res.statusText}`;
      throw new Error(`HTTP ${msg}`);
    }

    const text =
      typeof data?.text === 'string' ? data.text :
      (typeof data === 'string' ? data : JSON.stringify(data));

    this._mutateMessage(replyId, m => m.content = text ?? '');
  }

  /** Utility: mutate one message by id and trigger change detection. */
  private _mutateMessage(id: string, fn: (m: Message) => void) {
    this.messages.update(list => {
      const next = list.map(m => {
        if (m.id !== id) return m;
        const copy = { ...m };
        fn(copy);
        return copy;
      });
      return next;
    });
  }
}
