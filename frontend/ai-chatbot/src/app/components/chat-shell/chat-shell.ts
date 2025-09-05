import { Component } from '@angular/core';
import { ChatThread } from '../chat-thread/chat-thread';
import { Omnibox } from '../omnibox/omnibox';

@Component({
  selector: 'app-chat-shell',
  imports: [ChatThread, Omnibox],
  templateUrl: './chat-shell.html',
  styleUrl: './chat-shell.css'
})
export class ChatShell {

}
