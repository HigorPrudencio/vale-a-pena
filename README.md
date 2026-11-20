![Vale a Pena](docs/banner.png)

# Vale a Pena

App Android que avisa **na hora** se uma oferta de entrega ou corrida de moto compensa. Funciona com a **99** e a **Uber**.

Quando a oferta aparece, ele lê o valor e os km, faz a conta com os gastos da sua moto e mostra uma faixa colorida no topo da tela. Ele também fala o resultado em voz alta.

## ⬇️ Baixar

### [👉 Baixar o Vale a Pena (.apk)](https://github.com/HigorPrudencio/vale-a-pena/releases/latest/download/ValeAPena.apk)

Abra o link no celular Android. O download começa sozinho. Esse link sempre baixa a versão mais nova.

## Como ele avisa

| A faixa na oferta | A tela do app |
|---|---|
| <img src="docs/faixa-na-oferta.png" width="280"> | <img src="docs/tela-inicial.png" width="280"> |

- 🟢 **BOA:** bate a sua meta de lucro por hora
- 🔴 **RUIM:** não bate a meta, paga menos de R$ 1 por km, ou dá prejuízo
- ⚠️ **Entrega longe:** avisa quando o destino passa de 10 km, porque você pode voltar vazio

## O que ele calcula

- **Lucro real:** valor da oferta menos gasolina e desgaste da moto
- **R$ por hora:** conta o tempo da oferta mais um tempo extra pra estacionar, esperar e entregar
- **R$ por km:** soma os km até a coleta com os km da entrega
- **Resumo do dia:** entregas, km, gasolina, desgaste, lucro e lucro por hora
- **Histórico:** guarda 90 dias e copia tudo pra colar no Google Planilhas

Na primeira vez que você abre o app, ele pede um **cadastro rápido**: seu nome, sua moto, quantos km ela faz por litro, o preço da gasolina, quanto quer ganhar por dia e quantas horas quer trabalhar. Com isso ele calcula a sua meta por hora. Dá pra mudar depois em **Meus ajustes > Editar cadastro**.

## Instalar

1. Baixe o `ValeAPena.apk` pelo link acima
2. Toque no arquivo. Se o Android pedir, permita **instalar apps desta fonte**.
3. Se o **Play Protect** bloquear: abra a Play Store, toque na sua foto, entre em **Play Protect**, toque na engrenagem e desligue **Verificar apps**. Instale e **ligue de novo** depois.

O Play Protect bloqueia porque o app não está na Play Store e lê a tela. O código está todo aqui, aberto.

### Android 13 ou mais novo: "Acesso negado ao app"

Se aparecer **"Acesso negado ao app"** quando você tentar ativar o leitor de ofertas:

1. Toque em **Fechar**
2. Abra **Configurações > Apps > Vale a Pena**
3. Toque nos **3 pontinhos** no canto de cima à direita
4. Toque em **Permitir configurações restritas** e confirme com o PIN ou a digital
5. Volte no Vale a Pena e ative o leitor de novo

## Configurar (só na primeira vez)

Abra o app e ligue os 3 itens do quadro **Configuração**:

1. **Leitor de ofertas:** toque em **Ativar**, entre em "Vale a Pena: leitor de ofertas" e ative
2. **Leitura por imagem:** toque em **Ligar** e depois em **Iniciar agora**
3. **Bateria liberada:** toque em **Liberar** e escolha **Não otimizar**

Pronto. Fique online no app de entregas e trabalhe normalmente. Se o celular reiniciar, ligue a **leitura por imagem** de novo.

## Apps de banco

Alguns apps de banco bloqueiam o celular quando acham um app de fora da Play Store que lê a tela. Isso é uma proteção contra golpes, porque apps falsos usam esse mesmo recurso pra roubar senhas.

**Antes de abrir o banco:**
1. Abra o Vale a Pena e toque em **⏸ Pausar tudo para usar o banco**
2. Use o banco normalmente
3. Depois, volte no Vale a Pena e toque em **Ativar** e **Ligar** de novo

Se o banco continuar bloqueando mesmo com o app pausado, a única saída é desinstalar o Vale a Pena desse celular.

## Privacidade e segurança

- **Sem internet:** o app não tem permissão de internet. Confira em Configurações > Apps > Vale a Pena > Permissões. Nada sai do seu celular.
- **Só 99 e Uber:** o leitor só recebe informações desses dois apps. Ele não vê WhatsApp, banco nem nenhum outro app.
- A imagem da tela é lida na hora e apagada. Nada é gravado.
- O app **não aceita nem recusa** nada sozinho. Quem decide é você.
- **Baixe só por esta página.** Nunca instale um arquivo repassado por outra pessoa.
- **Assinatura oficial:** toda versão oficial é assinada com uma chave protegida por uma senha que só o dono do app tem. O código SHA-256 do certificado é:
  `A9:29:84:76:9D:5D:A1:AA:43:22:DD:5C:50:E3:DF:D9:DF:FB:0A:04:74:9D:62:8B:29:89:87:5D:EA:F8:D3:5F`
- Cada versão em **Releases** mostra o código de conferência do arquivo, pra provar que ele é o original.

## Atualizar

Baixe pelo mesmo link e instale por cima. Seus ajustes e o histórico continuam salvos.

---

Feito por **H.JVision**. App independente, sem nenhuma ligação com a 99, a Uber ou outras plataformas de entrega.
