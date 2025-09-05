import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Message } from '../../models/message';
import { LinkifyPipe } from '../../pipes/linkify.pipe';

@Component({
  selector: 'app-message-bubble',
  standalone:true,
  imports:[CommonModule, LinkifyPipe],
  templateUrl: './message-bubble.html',
  styleUrl: './message-bubble.css'
})
export class MessageBubble {
  @Input({required:true}) message!: Message;
}
