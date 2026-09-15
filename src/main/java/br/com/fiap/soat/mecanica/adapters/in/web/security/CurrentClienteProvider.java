package br.com.fiap.soat.mecanica.adapters.in.web.security;

import br.com.fiap.soat.mecanica.application.security.CurrentClientePort;
import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import br.com.fiap.soat.mecanica.domain.cliente.ClienteRepository;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentClienteProvider implements CurrentClientePort {

    private final ClienteRepository clienteRepository;

    @Override
    public Cliente get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof ClientePrincipal principal)) {
            throw new RuntimeException("Cliente não autenticado");
        }

        return clienteRepository.buscarPorId(principal.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
    }
}
