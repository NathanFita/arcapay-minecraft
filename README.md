# ArcaPay - Plugin Minecraft

Plugin de entrega automatica de produtos para servidores Minecraft (Spigot/Paper).

## Compatibilidade

- Spigot 1.20+
- Paper 1.20+
- Qualquer plugin de economia (Vault, EssentialsX, etc)
- Qualquer plugin de permissoes (LuckPerms, etc)

## Instalacao

1. Baixe o `.jar` da [Releases](../../releases) e coloque na pasta `plugins/`
2. Inicie o servidor (gera o `config.yml`)
3. Acesse o painel ArcaPay: **Integracoes > Scripts**
4. Gere um token e cole no `plugins/ArcaPay/config.yml`
5. Reinicie o servidor ou use `/arcapay reload`

## Configuracao

Edite `plugins/ArcaPay/config.yml`:

```yaml
token: "SEU_TOKEN_AQUI"
api-url: "https://arcapay.org/api/v1/fivem"
poll-interval: 10
identifier-type: "name"  # name | uuid
debug: false
```

## Comandos no produto

Configure os comandos no painel ArcaPay. O script executa como **console do servidor**.

### Exemplos

| Comando no ArcaPay | O que faz |
|---------------------|-----------|
| `give NathanFita diamond 64` | Da 64 diamantes (EssentialsX) |
| `lp user NathanFita parent set vip` | Da cargo VIP (LuckPerms) |
| `eco give NathanFita 1000` | Da $1000 (Vault/Essentials) |
| `kit give NathanFita vip_kit` | Da kit VIP |
| `whitelist add NathanFita` | Adiciona na whitelist |
| `op NathanFita` | Da OP (cuidado!) |

> Use o **nome do jogador** como variavel. Configure `$minecraft_name` nas Variaveis da loja.

### Variaveis

O cliente preenche na loja e o comando substitui automaticamente:
```
give $minecraft_name diamond 64
lp user $minecraft_name parent set $cargo
```

## Comandos in-game

| Comando | Permissao | Descricao |
|---------|-----------|-----------|
| `/arcapay status` | `arcapay.admin` | Mostra status |
| `/arcapay poll` | `arcapay.admin` | Forca polling manual |
| `/arcapay reload` | `arcapay.admin` | Recarrega config |

## Build (desenvolvedores)

Requer Java 17+ e Maven:
```bash
mvn clean package
```

O `.jar` sera gerado em `target/`.

## Suporte

- Painel: [arcapay.org](https://arcapay.org)
- Discord: [discord.gg/atlanta](https://discord.gg/atlanta)
