package br.com.serratec.ecommerce.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.serratec.ecommerce.dto.pedido.PedidoRequestDTO;
import br.com.serratec.ecommerce.dto.pedido.PedidoResponseDTO;
import br.com.serratec.ecommerce.dto.produto.ProdutoRequestDTO;
import br.com.serratec.ecommerce.model.EmailHtmlConteudo;
import br.com.serratec.ecommerce.model.Log;
import br.com.serratec.ecommerce.model.Pedido;
import br.com.serratec.ecommerce.model.PedidoItem;
import br.com.serratec.ecommerce.model.Produto;
import br.com.serratec.ecommerce.model.email.Email;
import br.com.serratec.ecommerce.repository.PedidoRepository;
import br.com.serratec.ecommerce.repository.ProdutoRepository;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoItemService pedidoItemService;

    @Autowired
    private LogService logService;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ProdutoService produtoService;

    @Autowired
    private ModelMapper mapper;

    @Autowired
    private EmailService emailService;

    public List<PedidoResponseDTO> obterTodos() {

        List<Pedido> pedidos = pedidoRepository.findAll();

        return pedidos
                .stream()
                .map(pedido -> mapper.map(pedido, PedidoResponseDTO.class))
                .collect(Collectors.toList());
    }

    public PedidoResponseDTO obterPorId(long id) {

        Optional<Pedido> optPedido = pedidoRepository.findById(id);

        if (optPedido.isEmpty()) {
            throw new RuntimeException("Nenhum registro encontrado para o ID: " + id);
        }

        return mapper.map(optPedido.get(), PedidoResponseDTO.class);
    }

    public PedidoResponseDTO adicionar(PedidoRequestDTO pedidoRequest) {

        // pega os itens
        List<PedidoItem> listaSalvaProdutos = pedidoRequest
                .getItens()
                .stream()
                .map(item -> mapper
                        .map(item, PedidoItem.class))
                .collect(Collectors.toList());

        Pedido pedido = adicionarPedido(pedidoRequest);

        pedido.setItens(listaSalvaProdutos);

        List<PedidoItem> itensCadastrados = itemsPedido(pedido);

        pedido.setItens(itensCadastrados);

        pedido = calcularValorTotalPedido(pedido);

        abaterEstoque(pedido);

        PedidoResponseDTO pedidoResponse = mapper.map(pedido, PedidoResponseDTO.class);

        pedidoRequest = mapper.map(pedido, PedidoRequestDTO.class);

        enviarEmailPedido(pedidoRequest);

        try {

            Log log = new Log(
                    "Pedido",
                    "CADASTRO",
                    "",
                    new ObjectMapper().writeValueAsString(pedido), pedido.getUsuario(),
                    new Date());

            logService.registrarLog(log);

        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return pedidoResponse;
    }

    public Pedido adicionarPedido(PedidoRequestDTO pedidoRequest) {

        Pedido pedido = mapper.map(pedidoRequest, Pedido.class);

        pedido.setDtPedido(new Date());
        pedido.setPedidoId(0l);
        pedido.setCancelado(false);

        pedido = pedidoRepository.save(pedido);

        return pedido;
    }

    public List<PedidoItem> itemsPedido(Pedido pedido) {

        List<PedidoItem> adicionadas = new ArrayList<>();

        for (PedidoItem pedidoItem : pedido.getItens()) {

            pedidoItem.setPedido(pedido);

            pedidoItemService.adicionar(pedidoItem);

            adicionadas.add(pedidoItem);
        }

        return adicionadas;
    }

    public Pedido calcularValorTotalPedido(Pedido pedido) {

        double ValorTotalPedido = 0;

        for (PedidoItem pedidoItem : pedido.getItens()) {

            ValorTotalPedido += pedidoItem.getVlToProd();
        }

        pedido.setVlTotal(ValorTotalPedido);

        return pedido;
    }

    public PedidoResponseDTO atualizar(long id, PedidoRequestDTO pedidoRequest) {

        // Se não lançar exception é porque o cara existe no banco.
        obterPorId(id);

        Pedido pedido = mapper.map(pedidoRequest, Pedido.class);

        pedido.setPedidoId(id);

        pedidoRepository.save(pedido);

        return mapper.map(pedido, PedidoResponseDTO.class);
    }

    public PedidoResponseDTO cancelarPedido(Long id) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        pedido.setCancelado(false);

        pedido = pedidoRepository.save(pedido);

        return mapper.map(pedido, PedidoResponseDTO.class);
    }

    public Pedido ativarPedido(Long id) {

        Pedido pedido = pedidoRepository.findById(id).orElseThrow();

        pedido.setCancelado(true);

        pedido = pedidoRepository.save(pedido);

        return pedido;
    }

    public void deletar(Long id) {

        obterPorId(id);

        pedidoRepository.deleteById(id);
    }

    public void abaterEstoque(Pedido pedido) {

        for (PedidoItem pedidoItem : pedido.getItens()) {

            Long id = pedidoItem.getProduto().getProdutoId();
            Optional<Produto> opProduto = produtoRepository.findById(id);
            int quantidadeItem = pedidoItem.getQtd();
            int quantidadeEstoque = opProduto.get().getQtdEst();

            if (quantidadeItem < quantidadeEstoque) {

                quantidadeEstoque -= quantidadeItem;

                ProdutoRequestDTO produtoRequest = mapper.map(opProduto.get(), ProdutoRequestDTO.class);

                produtoRequest.setQtdEst(quantidadeEstoque);

                produtoService.atualizar(id, produtoRequest);
            } else {
                throw new RuntimeException("quantidade inválida");
            }
        }
    }

    public String enviarEmailPedido(PedidoRequestDTO pedidoRequest) {
        
        Pedido pedido = mapper.map(pedidoRequest, Pedido.class);
        
        String destinatario = pedido.getUsuario().getEmail();
        String assunto = "Detalhes do seu pedido #" + pedido.getPedidoId();
        String mensagem = construirOConteudoDoEmail(pedido);

        Email email = new Email(assunto, mensagem, "d.conti133@gmail.com", Collections.singletonList(destinatario));

        EmailHtmlConteudo htmlConteudo = new EmailHtmlConteudo();
        htmlConteudo.salvarConteudoHtml(mensagem);

        try {
            emailService.enviar(email);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mensagem;
    }

    private String construirOConteudoDoEmail(Pedido pedido) {
        StringBuilder htmlConteudo = new StringBuilder();
        
        htmlConteudo.append("<!DOCTYPE html>\r\n" + //);
                "<html lang=\"pt-br\">\r\n" + //
                "<head>\r\n" + //
                "    <meta charset=\"UTF-8\">\r\n" + //
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\r\n" + //
                "    <title>G5 ecommerce</title>\r\n" + //
                "</head>\r\n" + //
                "<body style=\"width: 100%; height: 100%; font-family: Verdana,sans-serif;\">\r\n" + //
                "    <div id=\"emailBody\" style=\"background: #f2f2f2; color: #2f2f2f; width: 80%; max-width: 700px; position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); border-radius: 20px; box-shadow: 5px 5px 10px #444; overflow: auto;\">\r\n"
                + //
                "        <h1 style=\"text-align: center;\">Obrigado.</h1>\r\n" + //
                "        <div id=\"container\" style=\"background-color: white; text-align: center; padding: 10px; margin: 5%; border-radius: 10px;\">\r\n"
                + //
                "            <h2 style=\"text-align: center;\">Olá" + pedido.getUsuario().getNome() + "</h2>\r\n" + //
                "            <h3>Pedido Efetuado!</h3>\r\n" + //
                "            <p>\r\n" + //
                "                Obrigado por comprar de G5 Ecommerce.\r\n" + //
                "            </p>\r\n" + //
                "            <div box-shadow: 5px 5px 10px #444;>\r\n" + //
                "                <h3 style=\"color: #444; padding-bottom: 10px; border-bottom: 1px solid #2f2f2f; margin: 10px 2%; text-align: left;\">\r\n"
                + //
                "                    Informações do seu pedido: \r\n" + //
                "                </h3>\r\n" + //
                "                <p>\r\n" + //
                "                    <div style=\"text-align: left; margin: 20px; \">\r\n" + //
                "\r\n" + //
                "                        <div style=\"font-weight: bold; background-color: #444; color: white; padding: 10px;\">\r\n"
                + //
                "                            ID do pedido: \r\n" + //
                "                            <span style=\"color: white; font-weight: normal;\">" + pedido.getNrPedido() + "</span>\r\n"
                + //
                "                        </div>\r\n" + //
                "\r\n" + //
                "                        <div style=\"font-weight: bold; background-color: white; color: #444; padding: 10px;\">\r\n"
                + //
                "                            Enviar cobrança para: \r\n" + //
                "                            <span style=\"color: #2f2f2f; font-weight: normal;\\>" + pedido.getUsuario().getEmail() + "</span>\r\n"
                + //
                "                        </div>\r\n" + //
                "                        <div style=\"font-weight: bold; background-color: #444; color: white; padding: 10px;\">\r\n"
                + //
                "                            Data do pedido: \r\n" + //
                "                            <span style=\"color: white; font-weight: normal;\">" + pedido.getDtPedido() + "</span>\r\n"
                + //
                "                        </div>\r\n" + //
                "                    </div>\r\n" + //
                "                    \r\n" + //
                "                    <h3 style=\"color: #444; padding-bottom: 10px; border-bottom: 1px solid #2f2f2f; margin: 10px 2%; text-align: left;\">\r\n"
                + //
                "                        Aqui está o seu pedido: </h3>\r\n" + //
                "                    <div style=\"width: 100%; display: flex; flex-direction: column; align-items: center;\">\r\n"
                + //
                "                        <table style=\"width: 95%; padding: 10px; border-collapse: collapse;\">\r\n" + //
                "                            <thead style=\"background-color: #444; color: white; border: 1px solid #f2f2f2; width: 100%; height: 100%;\">\r\n"
                + //
                "                                <tr >\r\n" + //
                "                                    <th>Descrição</th>\r\n" + //
                "                                    <th>Quantidade</th>\r\n" + //
                "                                    <th>Preço</th>\r\n" + //
                "                                </tr>\r\n" + //
                "                            </thead>\r\n" + //
                "                            <tbody>\r\n");
                for (PedidoItem item : pedido.getItens()) {
                htmlConteudo.append("                <tr style=\"border: 1px solid #f2f2f2;\">\r\n" + //
                "                                    <td >" + item.getProduto().getProdNome() + "</td>\r\n" + //
                "                                    <td >" + item.getQtd() + "</td>\r\n" + //
                "                                    <td>R$" + item.getVlUn() + "</td>\r\n" + //
                "                                </tr>\r\n");
                }
                htmlConteudo.append("    </tbody>\r\n" + //
                "                        </table>\r\n" + //
                "                        <h4 style=\"border-top: 1px solid #2f2f2f; border-bottom: 1px solid #2f2f2f; padding: 20px; text-align: right;\">\r\n"
                + //
                "                            Total: R$" + pedido.getVlTotal() + "</h4>\r\n" + //
                "                    </div>\r\n" + //
                "                </p>\r\n" + //
                "            </div>\r\n" + //
                "        </div>\r\n" + //
                "    </div>\r\n" + //
                "</body>\r\n" + //
                "</html>");

        // htmlConteudo.append("<html><body>");
        // htmlConteudo.append("<table align=\"center\" border=\"1\" cellpadding=\"0\" cellspacing=\"0\" width=\"600\">");
        // htmlConteudo.append(
        //         "<tr><td align=\"center\" bgcolor=\"#70bbd9\" style=\"padding: 40px 0 30px 0;\" width=\"300\" height=\"230\">Grupo 5 E-Commerce</td></tr>");
        // htmlConteudo.append("<tr><td bgcolor=\"#87CEFA\" style=\"padding: 40px 30px 40px 30px;\">");
        // htmlConteudo.append("<table border=\"1\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">");
        // htmlConteudo.append("<tr><td>Olá " + pedido.getUsuario().getNome() + ",</td></tr>");
        // htmlConteudo.append("<tr><td><strong>Detalhes do Pedido #" + pedido.getNrPedido() + "</strong></td></tr>");
        // htmlConteudo.append("<tr><td>");
        // htmlConteudo.append("<table border=\"1\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">");
        // htmlConteudo.append("<tr><td width=\"260\" valign=\"top\"><strong>Itens do Pedido</strong></td>");
        // htmlConteudo.append("<td style=\"font-size: 0; line-height: 0;\" width=\"20\">&nbsp;</td>");
        // htmlConteudo.append("<td width=\"260\" valign=\"top\"><strong>Valor Unitário</strong></td></tr>");

        // for (PedidoItem item : pedido.getItens()) {
        //     htmlConteudo.append("<tr>");
        //     htmlConteudo.append("<td>" + item.getProduto().getProdNome() + " - Quantidade: " + item.getQtd() + "</td>");
        //     htmlConteudo.append("<td style=\"font-size: 0; line-height: 0;\" width=\"20\">&nbsp;</td>");
        //     htmlConteudo.append("<td>R$" + item.getVlUn() + "</td>");
        //     htmlConteudo.append("</tr>");
        // }

        // htmlConteudo.append("</table></td></tr>");
        // htmlConteudo.append("<tr><td bgcolor=\"#B0C4DE\" style=\"padding: 30px 30px 30px 30px;\">");
        // htmlConteudo.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\">");
        // htmlConteudo.append("<tr><td width=\"75%\">Grupo 5 Enterprises</td>");
        // htmlConteudo.append("<td align=\"right\"><table border=\"0\" cellpadding=\"0\" cellspacing=\"0\">");
        // htmlConteudo.append("<tr><td><a href=\"http://www.firjansenai.com.br/\">SENAI</a></td>");
        // htmlConteudo.append("<td style=\"font-size: 0; line-height: 0;\" width=\"20\">&nbsp;</td>");
        // htmlConteudo.append("<td><a href=\"http://www.serratec.org/\">SERRATEC</a></td></tr>");
        // htmlConteudo.append("</table></td></tr></table></td></tr></table></td></tr></table>");
        // </body></html>");

        return htmlConteudo.toString();
    }
}
