import { ChangeDetectorRef, Component, ElementRef, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { ASSISTANT_TOPICS, AssistantTopic, searchTopics } from './assistant-knowledge';

interface ChatMessage {
  from: 'user' | 'bot';
  text?: string;
  steps?: string[];
  link?: { commands: string[]; queryParams?: Record<string, string> } | null;
  suggestions?: AssistantTopic[];
}

type SpeechRecognitionLike = {
  lang: string;
  interimResults: boolean;
  onresult: ((event: any) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
};

/** Asistente de ayuda flotante: responde "¿cómo hago...?" con pasos y un acceso directo a la pantalla. */
@Component({
  selector: 'app-assistant-widget',
  standalone: true,
  imports: [FormsModule, RouterLink, TranslatePipe],
  templateUrl: './assistant-widget.html',
  styleUrl: './assistant-widget.css',
})
export class AssistantWidget {
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  protected readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  @ViewChild('messagesBox') private messagesBox?: ElementRef<HTMLElement>;

  protected open = false;
  protected question = '';
  protected listening = false;
  protected readonly faq = ASSISTANT_TOPICS.slice(0, 6);
  protected messages: ChatMessage[] = [];
  private recognition: SpeechRecognitionLike | null = null;

  protected get voiceSupported(): boolean {
    return typeof window !== 'undefined' && !!((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition);
  }

  protected toggle(): void {
    this.open = !this.open;
    if (this.open && this.messages.length === 0) {
      this.messages.push({
        from: 'bot',
        text: this.i18n.t(
          'Hola, soy el asistente del sistema. Pregúntame cómo hacer algo, por ejemplo "¿cómo abro una caja?".',
        ),
        suggestions: this.faq,
      });
    }
  }

  protected ask(text?: string): void {
    const query = (text ?? this.question).trim();
    if (!query) return;
    this.question = '';
    this.messages.push({ from: 'user', text: query });

    const results = searchTopics(query);
    if (results.length === 0) {
      this.messages.push({
        from: 'bot',
        text: 'No encontré una respuesta exacta. Prueba con otras palabras o elige uno de estos temas:',
        suggestions: this.faq,
      });
    } else {
      this.answer(results[0], results.slice(1, 4));
    }
    this.scrollToEnd();
  }

  protected pick(topic: AssistantTopic): void {
    this.messages.push({ from: 'user', text: topic.question });
    this.answer(topic, []);
    this.scrollToEnd();
  }

  protected speak(message: ChatMessage): void {
    if (!('speechSynthesis' in window)) return;
    const text = [message.text, ...(message.steps ?? [])].filter(Boolean).join('. ');
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'es-ES';
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
  }

  protected toggleVoice(): void {
    if (this.listening) {
      this.recognition?.stop();
      return;
    }
    const Ctor = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!Ctor) return;
    const recognition: SpeechRecognitionLike = new Ctor();
    recognition.lang = this.i18n.language() === 'en' ? 'en-US' : this.i18n.language() === 'pt' ? 'pt-BR' : 'es-ES';
    recognition.interimResults = false;
    recognition.onresult = (event: any) => {
      const transcript = event.results?.[0]?.[0]?.transcript ?? '';
      this.ask(transcript);
      this.cdr.detectChanges();
    };
    recognition.onerror = () => {
      this.listening = false;
      this.cdr.detectChanges();
    };
    recognition.onend = () => {
      this.listening = false;
      this.cdr.detectChanges();
    };
    this.recognition = recognition;
    this.listening = true;
    recognition.start();
  }

  private answer(topic: AssistantTopic, related: AssistantTopic[]): void {
    this.messages.push({
      from: 'bot',
      text: topic.question,
      steps: topic.answer,
      link: this.linkFor(topic),
      suggestions: related,
    });
  }

  private linkFor(topic: AssistantTopic): ChatMessage['link'] {
    if (topic.route) {
      const companyId = this.currentCompanyId();
      const queryParams = topic.route[0] === '/administrator/settings' && companyId ? { companyId } : topic.queryParams;
      return { commands: topic.route, queryParams };
    }
    if (topic.companyRoute) {
      const companyId = this.currentCompanyId();
      return companyId ? { commands: ['/company', companyId, ...topic.companyRoute] } : null;
    }
    return null;
  }

  private currentCompanyId(): string {
    const match = this.router.url.match(/\/company\/(\d+)/);
    if (match) return match[1];
    const query = this.router.url.match(/companyId=(\d+)/);
    return query ? query[1] : '';
  }

  private scrollToEnd(): void {
    this.cdr.detectChanges();
    setTimeout(() => {
      const box = this.messagesBox?.nativeElement;
      if (box) box.scrollTop = box.scrollHeight;
    });
  }
}
