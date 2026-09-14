package br.com.fiap.soat.mecanica.adapters.out.notification;

import br.com.fiap.soat.mecanica.application.ordemServico.NotificacaoOrdemServicoPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.NotificacaoOrdemServico;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpNotificacaoOrdemServicoAdapter implements NotificacaoOrdemServicoPort {

    private final JavaMailSender javaMailSender;

    @Value("${app.notification.email.from:no-reply@mecanica.local}")
    private String remetente;

    @Override
    public void enviar(NotificacaoOrdemServico notificacao) {
        validarEndereco(remetente, "remetente");
        validarEndereco(notificacao.emailCliente(), "destinatario");

        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(notificacao.emailCliente());
        mensagem.setSubject("Atualização da ordem de serviço " + notificacao.ordemServicoId());
        mensagem.setText("""
                Olá, %s.

                A situação da sua ordem de serviço %s foi atualizada.

                Situação anterior: %s
                Nova situação: %s

                Esta é uma mensagem automática da oficina.
                """.formatted(
                notificacao.nomeCliente(),
                notificacao.ordemServicoId(),
                notificacao.situacaoAnterior(),
                notificacao.novaSituacao()
        ));

        javaMailSender.send(mensagem);
    }

    private void validarEndereco(String endereco, String campo) {
        if (endereco == null || endereco.isBlank() || endereco.contains("\r") || endereco.contains("\n")) {
            throw new IllegalArgumentException("Endereco de " + campo + " invalido");
        }

        try {
            new InternetAddress(endereco, true);
        } catch (AddressException ex) {
            throw new IllegalArgumentException("Endereco de " + campo + " invalido", ex);
        }
    }
}
