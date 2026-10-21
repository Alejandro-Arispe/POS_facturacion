package com.pos.notifications.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pos.notifications.dto.NotificationDtos;
import com.pos.notifications.service.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/register-token")
    public Map<String, Object> register(@RequestBody NotificationDtos.RegisterTokenRequest request) {
        return notificationService.registerToken(request);
    }

    @PostMapping("/send-alert")
    public Map<String, Object> sendAlert(@RequestBody NotificationDtos.SendAlertRequest request) {
        return notificationService.enviarAlerta(
                request.getIdEmpresa(), request.getTitulo(), request.getMensaje(), request.getPayload());
    }

    @GetMapping("/history/empresas/{idEmpresa}")
    public Map<String, Object> history(
            @PathVariable Integer idEmpresa,
            @RequestParam(name = "id_usuario", required = false) Integer idUsuario) {
        return notificationService.history(idEmpresa, idUsuario);
    }

    @PostMapping("/mark-read")
    public Map<String, Object> markRead(@RequestBody NotificationDtos.MarkReadRequest request) {
        return notificationService.markRead(request.getId());
    }
}
