package br.com.fiap.soat.mecanica.adapters.out.notification;

import br.com.fiap.soat.mecanica.application.ordemServico.dto.NotificacaoOrdemServico;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SmtpNotificacaoOrdemServicoAdapterTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    void deveEnviarEmailComCabecalhosECorpoCorretos() {
        SmtpNotificacaoOrdemServicoAdapter adapter = criarAdapter("no-reply@mecanica.local");
        UUID ordemServicoId = UUID.randomUUID();
        NotificacaoOrdemServico notificacao = new NotificacaoOrdemServico(
                ordemServicoId,
                "Cliente Teste",
                "cliente@email.com",
                SituacaoOrdemServicoEnum.EM_EXECUCAO,
                SituacaoOrdemServicoEnum.FINALIZADA
        );

        adapter.enviar(notificacao);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage mensagem = captor.getValue();
        assertThat(mensagem.getFrom()).isEqualTo("no-reply@mecanica.local");
        assertThat(mensagem.getTo()).containsExactly("cliente@email.com");
        assertThat(mensagem.getSubject()).isEqualTo("Atualização da ordem de serviço " + ordemServicoId);
        assertThat(mensagem.getText())
                .contains("Olá, Cliente Teste.")
                .contains("Situação anterior: Em execução")
                .contains("Nova situação: Finalizada")
                .doesNotContain("EM_EXECUCAO", "FINALIZADA");
    }

    @Test
    void deveRejeitarCrLfOuEnderecoInvalido() {
        SmtpNotificacaoOrdemServicoAdapter adapter = criarAdapter("no-reply@mecanica.local");
        NotificacaoOrdemServico notificacao = new NotificacaoOrdemServico(
                UUID.randomUUID(),
                "Cliente Teste",
                "cliente@email.com\r\nBcc: atacante@email.com",
                SituacaoOrdemServicoEnum.RECEBIDA,
                SituacaoOrdemServicoEnum.EM_DIAGNOSTICO
        );

        assertThatThrownBy(() -> adapter.enviar(notificacao))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(javaMailSender);
    }

    private SmtpNotificacaoOrdemServicoAdapter criarAdapter(String remetente) {
        SmtpNotificacaoOrdemServicoAdapter adapter = new SmtpNotificacaoOrdemServicoAdapter(javaMailSender);
        ReflectionTestUtils.setField(adapter, "remetente", remetente);
        return adapter;
    }
}
