package br.com.serratec.ecommerce.dto.formaDePagamento;

public class FormaDePagamentoResponseDTO {

    private String codPgt;
    private String descricao;

    // #region Getter's and setter's
    public String getCodPgt() {
        return codPgt;
    }

    public void setCodPgt(String codPgt) {
        this.codPgt = codPgt;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }
    // #endregion
}
