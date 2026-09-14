package br.com.fiap.soat.mecanica.application.ordemServico.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacaoTest {

    @Test
    void deveAceitarLimitesValidos() {
        assertThat(new Paginacao(0, 1)).isNotNull();
        assertThat(new Paginacao(0, 100)).isNotNull();
    }

    @Test
    void deveRejeitarPaginaNegativa() {
        assertThatThrownBy(() -> new Paginacao(-1, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarTamanhoForaDosLimites() {
        assertThatThrownBy(() -> new Paginacao(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Paginacao(0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
