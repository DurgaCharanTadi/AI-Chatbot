export type Role = 'user' | 'assistant' | 'system' | 'tool';


export interface FileRef {
    name: string;
    size: number; // bytes
    type: string;
}


export interface Message {
    id: string;
    role: Role;
    content: string;
    timestamp: number; // epoch ms
    attachments?: FileRef[];
}