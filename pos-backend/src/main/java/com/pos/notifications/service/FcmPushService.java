package com.pos.notifications.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;
import com.pos.notifications.repo.DispositivoTokenRepository;

import jakarta.annotation.PreDestroy;

/**
 * Envio real de notificaciones push via Firebase Cloud Messaging (HTTP v1).
 *
 * <p>Los envios se hacen DESPUES del commit de la transaccion que los origina y en un hilo aparte,
 * para que un fallo de red con FCM nunca revierta una venta o un movimiento de inventario.
 * Los tokens que FCM reporta como invalidos / no registrados se eliminan de la base.
 */
@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    /** Debe coincidir con el canal creado en la app movil (FcmGlobalService) y en el AndroidManifest. */
    static final String ANDROID_CHANNEL_ID = "credit_payments_channel_v2";
    private static final int FCM_MAX_TOKENS_PER_CALL = 500;

    private final DispositivoTokenRepository tokenRepository;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fcm-push");
        t.setDaemon(true);
        return t;
    });
    private final FirebaseMessaging messaging;

    public FcmPushService(DispositivoTokenRepository tokenRepository,
            @Value("${app.firebase.credentials-path:}") String credentialsPath) {
        this.tokenRepository = tokenRepository;
        this.messaging = init(credentialsPath);
    }

    public boolean isEnabled() {
        return messaging != null;
    }

    /** Programa el envio para despues del commit (o inmediato si no hay transaccion activa). */
    public void sendAfterCommit(List<String> tokens, String title, String body, Map<String, Object> data) {
        if (!isEnabled() || tokens == null || tokens.isEmpty()) {
            return;
        }
        List<String> targets = tokens.stream().filter(t -> t != null && !t.isBlank()).distinct().toList();
        if (targets.isEmpty()) {
            return;
        }
        Map<String, String> stringData = toStringData(data);
        Runnable task = () -> executor.execute(() -> send(targets, title, body, stringData));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void send(List<String> tokens, String title, String body, Map<String, String> data) {
        for (int i = 0; i < tokens.size(); i += FCM_MAX_TOKENS_PER_CALL) {
            List<String> chunk = tokens.subList(i, Math.min(i + FCM_MAX_TOKENS_PER_CALL, tokens.size()));
            try {
                MulticastMessage message = MulticastMessage.builder()
                        .addAllTokens(chunk)
                        .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                        .putAllData(data)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setChannelId(ANDROID_CHANNEL_ID)
                                        .setSound("default")
                                        .build())
                                .build())
                        .setWebpushConfig(WebpushConfig.builder()
                                .putHeader("Urgency", "high")
                                .setNotification(WebpushNotification.builder()
                                        .setTitle(title).setBody(body).build())
                                .build())
                        .build();
                BatchResponse response = messaging.sendEachForMulticast(message);
                List<String> invalid = new ArrayList<>();
                List<SendResponse> responses = response.getResponses();
                for (int j = 0; j < responses.size(); j++) {
                    SendResponse r = responses.get(j);
                    if (!r.isSuccessful() && r.getException() != null) {
                        MessagingErrorCode code = r.getException().getMessagingErrorCode();
                        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT
                                || code == MessagingErrorCode.SENDER_ID_MISMATCH) {
                            invalid.add(chunk.get(j));
                        } else {
                            log.warn("[FCM] Fallo enviando a un token: {} {}", code, r.getException().getMessage());
                        }
                    }
                }
                for (String token : invalid) {
                    tokenRepository.deleteByToken(token);
                }
                log.info("[FCM] '{}' enviados={} fallidos={} tokens_invalidos_eliminados={}",
                        title, response.getSuccessCount(), response.getFailureCount(), invalid.size());
            } catch (Exception ex) {
                log.error("[FCM] Error enviando notificacion '{}': {}", title, ex.getMessage(), ex);
            }
        }
    }

    private static Map<String, String> toStringData(Map<String, Object> data) {
        Map<String, String> out = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(k, String.valueOf(v));
                }
            });
        }
        return out;
    }

    private static FirebaseMessaging init(String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("[FCM] app.firebase.credentials-path vacio: notificaciones push DESACTIVADAS.");
            return null;
        }
        Path path = Path.of(credentialsPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.warn("[FCM] No se encontro la cuenta de servicio en {}: notificaciones push DESACTIVADAS.", path);
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in);
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> FirebaseApp.DEFAULT_APP_NAME.equals(a.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(
                            FirebaseOptions.builder().setCredentials(credentials).build()));
            log.info("[FCM] Firebase inicializado ({}). Notificaciones push ACTIVADAS.", path.getFileName());
            return FirebaseMessaging.getInstance(app);
        } catch (Exception ex) {
            log.error("[FCM] No se pudo inicializar Firebase con {}: {}", path, ex.getMessage());
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
