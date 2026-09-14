package br.com.fiap.soat.mecanica.adapters.out.persistence.ordemServico;

import br.com.fiap.soat.mecanica.adapters.out.persistence.ordemServico.mapper.OrdemServicoMapper;
import br.com.fiap.soat.mecanica.application.ordemServico.ListarOrdensServicoAtivasPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Paginacao;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrdemServicoRepositoryImpl implements OrdemServicoRepository, ListarOrdensServicoAtivasPort {

    private final OrdemServicoJpaRepository ordemServicoJpaRepository;
    private final OrdemServicoMapper ordemServicoMapper;

    @Override
    public OrdemServico salvar(OrdemServico ordemServico) {
        OrdemServicoEntity ordemServicoEntity = ordemServicoMapper.toEntity(ordemServico);
        ordemServicoJpaRepository.save(ordemServicoEntity);
        return ordemServicoMapper.toDomain(ordemServicoEntity);
    }

    @Override
    public Optional<OrdemServico> buscarPorId(UUID id) {
        return ordemServicoJpaRepository.findById(id)
                .map(ordemServicoMapper::toDomain);
    }

    @Override
    public List<OrdemServico> buscarTodosPorVeiculoId(UUID veiculoId) {
        return ordemServicoJpaRepository.findAllByVeiculoId(veiculoId)
                .stream()
                .map(ordemServicoMapper::toDomain)
                .toList();
    }

    @Override
    public Pagina<OrdemServico> listarAtivas(Paginacao paginacao) {
        Page<OrdemServicoEntity> resultado = ordemServicoJpaRepository.listarAtivasOrdenadas(
                StatusRecursoEnum.ATIVO,
                SituacaoOrdemServicoEnum.EM_EXECUCAO,
                SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO,
                SituacaoOrdemServicoEnum.EM_DIAGNOSTICO,
                SituacaoOrdemServicoEnum.RECEBIDA,
                PageRequest.of(paginacao.page(), paginacao.size())
        );

        List<OrdemServico> content = resultado.getContent().stream()
                .map(ordemServicoMapper::toDomain)
                .toList();

        return new Pagina<>(
                content,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                resultado.isFirst(),
                resultado.isLast()
        );
    }
}
