# Vale a Pena

App Android que lê as ofertas de Entrega Moto da 99 e avisa se a entrega compensa.

- Lê valor, km até a coleta e km da entrega direto da tela
- Mostra uma faixa verde, amarela ou vermelha no topo da tela
- Fala o resultado em voz alta
- Configurado para Yamaha Fazer 150 (35 km/l), gasolina R$ 6,39
- Não aceita nem recusa nada sozinho

## Gerar o APK pelo GitHub (sem computador potente)

1. Crie um repositório no GitHub e envie todos estes arquivos
2. O GitHub gera o APK sozinho (aba **Actions**)
3. Baixe o `ValeAPena.apk` na aba **Releases**

## Gerar o APK no computador

1. Abra a pasta no Android Studio
2. Menu **Build > Build APK(s)**

## Instalar no celular

1. Abra o `ValeAPena.apk` no Android e permita "instalar apps desconhecidos"
2. Abra o app e toque em **Ativar leitor de ofertas**
3. Em Acessibilidade, ative **Vale a Pena: leitor de ofertas**
4. Toque em **Testar com oferta de exemplo**: tem que aparecer a faixa verde e falar "Entrega boa"
