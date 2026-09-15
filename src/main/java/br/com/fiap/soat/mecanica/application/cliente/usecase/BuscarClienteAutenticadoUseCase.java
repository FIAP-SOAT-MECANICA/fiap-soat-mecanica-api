package br.com.fiap.soat.mecanica.application.cliente.usecase;

import br.com.fiap.soat.mecanica.application.security.CurrentClientePort;
import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarClienteAutenticadoUseCase {

    private final CurrentClientePort currentCliente;

    public Cliente executar() {
        return currentCliente.get();
    }
}
