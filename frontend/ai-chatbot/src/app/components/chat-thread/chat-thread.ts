import {
  Component,
  inject,
  computed
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Chat } from '../../services/chat';
import { Message } from '../../models/message';
import { MessageBubble } from '../message-bubble/message-bubble';
import { TypingIndicator } from '../typing-indicator/typing-indicator';

@Component({
  selector: 'app-chat-thread',
  standalone: true,
  imports: [CommonModule, MessageBubble, TypingIndicator],
  templateUrl: './chat-thread.html',
  styleUrl: './chat-thread.css'
})
export class ChatThread {
  // Service & signals
  chat = inject(Chat);
  msgs = computed(() => this.chat.messages());
  streaming = this.chat.isStreaming;

}
