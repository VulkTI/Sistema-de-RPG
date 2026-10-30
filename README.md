# Sistema RPG

Mod para Fabric em Minecraft 26.3 que adiciona progressão de jogador e de classe, perks e afixos configuráveis por recursos JSON.

## Neste README

- [Instalação](#requisitos-e-instalação)
- [Comandos](#comandos)
- [Classes e perks](#classes-e-perks)
- [Sistema de afixos](#sistema-de-afixos)
- [Níveis de criaturas e zonas](#níveis-de-criaturas)
- [Geração de minérios por zonas](#geração-de-minérios-por-zonas)
- [Guia para desenvolvimento](#guia-para-desenvolvimento-e-criação-de-conteúdo)

## Requisitos e instalação

- Minecraft 26.3
- Fabric Loader 0.20.1 ou superior e Fabric API
- Java 25 ou superior

Para compilar o projeto no Windows, execute `./gradlew.bat build` (ou `compilar.bat`). O JAR fica em `build/libs`. Instale-o junto com Fabric API na pasta `mods` do perfil Fabric. O mod funciona nos ambientes cliente e servidor; dados de progressão são controlados pelo servidor.

## Conteúdo atual

- XP e nível do jogador, além de XP, nível e pontos de habilidade por classe. Perfis são salvos no mundo por UUID do jogador.
- Seleção de classe no primeiro acesso, tela de status e tela genérica para árvore de habilidades.
- Duas classes registradas: **Ferreiro**, voltado a fundição e fabricação, e **Minerador**, voltado a escavação e coleta de minérios.
- Sistema de afixos em armas, ferramentas, armaduras e outros itens compatíveis: modificadores de atributos, raridades, grupos, tags, itens específicos, tooltips e rolagem automática.
- Herança de afixo ao aprimorar um item na ferraria; se o item-base não tiver afixo, o resultado pode receber um novo.
- Atributo `rpgsystem:affix_luck`, comandos de administração e sincronização dos dados entre servidor e cliente.
- Níveis persistentes de mobs, definidos por bioma/estrutura, escalonamento de combate e etiqueta do alvo sob a mira.

O XP do jogador aumenta em 8 ao derrotar uma criatura da categoria MONSTER. A classe ativa pode receber XP adicional conforme a ação. O Ferreiro recebe XP ao coletar certos lingotes/nuggets fundidos e ao fabricar itens metálicos. A curva de XP começa em 100 para avançar do nível 1, aumentando 5 XP mais a metade do quadrado do número de níveis já avançados; o nível máximo é 100. Cada novo nível de classe concede 2 pontos de habilidade. A classe Ferreiro atual retorna uma árvore vazia, então não há habilidades para desbloquear nela no momento.

O botão **P** abre a tela de status. Use `/rpg menu` para escolher uma classe (também abre a seleção ao entrar sem classe). Para trocar de classe, use `/rpg class set ferreiro` ou `/rpg class set minerador`.

Os valores de XP são configurados em `config/rpgsystem_xp.json` na pasta do jogo. O arquivo é criado automaticamente na primeira inicialização do mod; edite os números e reinicie o jogo/servidor para aplicar. Os IDs dos minérios não incluem o prefixo `minecraft:`.

## Comandos

### Classe e telas

```text
/rpg menu
/rpg class set <classe>
/rpg class reset
```

`/rpg class reset` remove a classe ativa, mas preserva XP e progressos guardados. `/rpg menu` abre a seleção de classe; a tela de status também pode ser aberta com **P**.

### XP e reset de jogador

Os comandos a seguir exigem permissão de moderador. Sem alvo, a operação vale para quem executa o comando; com `<jogador>`, pode-se selecionar um ou vários jogadores aceitos pelo argumento vanilla `players`.

```text
/rpg xp player add <quantidade> points
/rpg xp player remove <quantidade> levels
/rpg xp player set <quantidade> points
/rpg xp class add <quantidade> levels
/rpg xp class remove <jogador> <quantidade> points
/rpg xp class set <quantidade> points
/rpg reset <jogador>
```

As mesmas operações de XP aceitam alvo opcional antes da quantidade, por exemplo `/rpg xp player add Steve 100 points` ou `/rpg xp class add Steve 1 levels`. `points` altera XP bruto; `levels` altera o nível, mantendo o progresso da barra quando possível. XP de classe só pode ser alterado para jogadores com uma classe ativa. `/rpg reset` apaga classe, XP, pontos e habilidades do alvo.

### Afixos

Os comandos de afixo atuam sobre o item na mão principal:

```text
/rpg affix list
/rpg affix roll
/rpg affix roll rarity <comum|incomum|raro|epico|lendario|mitico>
/rpg affix roll <id_do_afixo>
/rpg affix apply <id_do_afixo>
/rpg affix remove
```

`list` mostra os afixos compatíveis e as chances individuais estimadas na rolagem normal. `roll` faz uma rolagem normal, incluindo chance de nenhum afixo. `roll rarity` escolhe uniformemente entre afixos compatíveis daquela raridade, sem a rolagem normal de raridade/ausência. `roll <id>` simula rolagens normais até obter o afixo indicado e aplica o resultado; serve para teste e pode levar muitas rolagens simuladas. `apply` aplica diretamente um afixo compatível e `remove` retira o afixo atual.

## Classes e perks

As classes definem identidade, regras de XP, perks e árvore de habilidades. Atualmente, o mod inclui Ferreiro e Minerador.

### Classe Ferreiro incluída

- Recebe XP por lingotes e pepitas de cobre, ferro e ouro coletados da fundição. Cada barra vale, respectivamente, 4, 10 e 15 XP; pepitas valem um nono desses valores, arredondado para a pilha.
- Recebe XP ao fabricar lingotes de netherita e itens do grupo `Metal`. Itens metálicos também ganham 10% de durabilidade máxima. A fórmula de XP por item usa base 10, bônus por material (ferro +10, ouro +20, diamante +35, netherita +50) e mais 30% por barra de metal usada; lingotes de netherita dão adicionalmente 500 XP por unidade.
- A perk `Afixo de Mestre` reduz pela metade a chance-base de não receber afixo ao retirar resultados de crafting. A perk `Metal Reforçado` descreve durabilidade, aplicada atualmente aos resultados do grupo `Metal`. A perk `Reparação Eficiente` está descrita na interface, mas sua chave ainda não tem rotina de reparação ligada.
- Ao aprimorar na ferraria, o afixo do item-base é preservado. Sem afixo herdado, o resultado pode receber um afixo novo, com chance-base reduzida pela metade.

As perks ficam em `src/main/resources/data/rpgsystem/perks/` e são carregadas pelo ID referenciado pela classe.

### Classe Minerador incluída

- Recebe XP ao quebrar minérios vanilla sem Toque Suave, incluindo as variantes de ardosiabissal: carvão 2; cobre e quartzo 3; redstone e lápis-lazúli 4; ferro 5; ouro e ouro do Nether 6; esmeralda 12; diamante 16; e detritos ancestrais 40 XP.
- Pedra e ardosiabissal sem Toque Suave acumulam progresso; a cada 64 blocos, concedem 4 XP. Esse contador é salvo no perfil do jogador, portanto não se perde ao desconectar ou trocar de classe.
- **Picareta Conservada:** ao gastar durabilidade de uma picareta há 10% de chance adicional de não a gastar. A rolagem acontece antes da durabilidade vanilla, por isso se combina com Inquebrável.
- **Golpes Precisos:** concede 5% de velocidade global para quebrar blocos enquanto Minerador é a classe ativa.
- **Veio Rico:** ao quebrar um minério sem Toque Suave, há 1% de chance de receber um recurso mineral extra. O bônus entrega uma unidade do recurso-base do minério e não reaplica Fortuna.

## Sistema de afixos

Cada JSON de afixo é carregado como recurso de dados. O nome do arquivo forma o ID: `data/rpgsystem/affixes/afiado.json` corresponde a `rpgsystem:afiado`.

## Rolagem, atributo e criação de itens

Uma rolagem normal começa com 33% de chance de não gerar afixo. Com `rpgsystem:affix_luck = 0`, os pesos-base das raridades são 60, 25, 10, 4, 0.9 e 0.1, nessa ordem, normalizados entre todas as seis raridades. Se um item não tiver afixos compatíveis da raridade sorteada, a rolagem também fica vazia. Depois da raridade, um afixo compatível é escolhido uniformemente dentro daquela raridade.

O modificador interno de sorte é `max(0, 1 + affix_luck)`: valores positivos reduzem a chance de nenhum afixo e aumentam o peso relativo de raridades altas; -1 ou menos torna a chance de nenhum afixo 100%. Exemplo para testar:

```text
/attribute @s rpgsystem:affix_luck base set 1
```

Isso afeta a rolagem, sem mudar compatibilidade. A perk do Ferreiro reduz em 50% a chance-base de ausência nos resultados retirados de crafting. Na ferraria, afixos do item-base são herdados; quando não há afixo para herdar, usa-se a rolagem de afixo com a mesma redução de ausência.

### Grupos de itens

Os grupos são definidos em `src/main/resources/data/<namespace>/item_groups/<nome>.json`. O arquivo `metal.json`, por exemplo, pode ser referenciado como `Metal` ou `metal`. Os grupos padrão incluem armas corpo a corpo (`Swords`, `Spears`, `Axes`), ferramentas (`Pickaxes`, `Shovels`, `Hoes`), armaduras por peça (`Helmets`, `Chestplates`, `Leggings`, `Boots`), agregador `Armors` e materiais `Metal` e `Leather`.

Cada grupo pode ter `items`, `tags` e `groups` opcionais:

```json
{
  "tags": ["minecraft:swords", "c:bows"],
  "items": ["minecraft:trident", "#c:spears"],
  "groups": ["axes"]
}
```

Grupos podem referenciar outros grupos. Uma entrada com espaço exige interseção, por exemplo `"Leather Armors"` aceita itens que estejam nos dois grupos; `"Metal #minecraft:swords"` combina o grupo Metal com a tag. Entradas diferentes na lista continuam sendo alternativas. O grupo virtual `All` representa a união dos grupos JSON carregados. Após alterar os arquivos, execute `/reload`.

## Níveis de criaturas

Todo `Mob` vanilla ou de mod que possua atributos compatíveis recebe uma única vez um nível entre 1 e 100. O servidor é a autoridade: ele persiste `rpgsystem.mob_level` nos dados da entidade e o sincroniza como dado de entidade vanilla. Assim, descarregar chunks, reiniciar o mundo, trocar de dimensão, mudar de bioma ou renomear a criatura não altera seu nível. Mobs de mundos antigos sem esse dado são inicializados na primeira vez que forem carregados pela versão nova, usando a posição daquela inicialização.

A resolução é sempre: chefe fixo, peça de estrutura que contém o ponto, bioma de inicialização e, por último, `fallback_range`. Estruturas são consultadas apenas na inicialização e somente quando a posição está dentro de uma peça da estrutura — estar perto dela não basta. Em sobreposição, a ordem determinística é Ancient City, End City, bastião, fortaleza e Trial Chambers.

### Zonas e biomas padrão

As zonas abaixo são as faixas de progressão usadas para definir o nível dos mobs. A zona é determinada pelo bioma em que a criatura é inicializada; estruturas com regra própria têm prioridade. Os IDs foram validados contra os dados de Minecraft 26.3 incluídos pelas dependências do projeto.

#### Zona 1 — níveis 1–14 | Inicial e mundo seguro

Áreas de entrada, com florestas, praias, rios, oceanos rasos e cavernas exuberantes. Os únicos minérios são: Cobre e carvão

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:plains` | 1–5 |
| `minecraft:sunflower_plains` | 1–5 |
| `minecraft:mushroom_fields` | 1–6 |
| `minecraft:beach` | 2–7 |
| `minecraft:river` | 2–7 |
| `minecraft:ocean` | 2–8 |
| `minecraft:forest` | 3–8 |
| `minecraft:flower_forest` | 3–8 |
| `minecraft:lukewarm_ocean` | 3–9 |
| `minecraft:birch_forest` | 4–9 |
| `minecraft:deep_ocean` | 4–10 |
| `minecraft:deep_lukewarm_ocean` | 4–10 |
| `minecraft:dappled_forest` | 5–11 |
| `minecraft:meadow` | 5–11 |
| `minecraft:cherry_grove` | 6–12 |
| `minecraft:swamp` | 6–12 |
| `minecraft:old_growth_birch_forest` | 7–13 |
| `minecraft:lush_caves` | 8–14 |

#### Zona 2: Regiões áridas — níveis 14–34

Reúne terrenos mais hostis e diversos, como desertos, selvas, pântanos de mangue, savanas, badlands e montanhas. Os minérios predominantes são: Ferro, lápis-lazuli e redstone

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:stony_shore` | 14–18 |
| `minecraft:cold_ocean` | 14–18 |
| `minecraft:warm_ocean` | 14–18 |
| `minecraft:windswept_hills` | 15–19 |
| `minecraft:windswept_forest` | 15–20 |
| `minecraft:deep_cold_ocean` | 16–20 |
| `minecraft:dark_forest` | 17–21 |
| `minecraft:mangrove_swamp` | 17–22 |
| `minecraft:windswept_gravelly_hills` | 18–22 |
| `minecraft:sparse_jungle` | 19–23 |
| `minecraft:pale_garden` | 20–24 |
| `minecraft:jungle` | 21–25 |
| `minecraft:savanna` | 22–26 |
| `minecraft:bamboo_jungle` | 23–27 |
| `minecraft:savanna_plateau` | 24–28 |
| `minecraft:desert` | 25–29 |
| `minecraft:windswept_savanna` | 26–30 |
| `minecraft:wooded_badlands` | 27–31 |
| `minecraft:stony_peaks` | 28–32 |
| `minecraft:badlands` | 29–33 |
| `minecraft:sulfur_caves` | 30–34 |
| `minecraft:eroded_badlands` | 30–34 |

#### Zona 3: Regiões Frias — níveis 35–59

É dominada por taigas, neve, oceanos congelados e picos de montanha. Os minérios predominantes são: Diamante e ouro

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:taiga` | 35–39 |
| `minecraft:old_growth_pine_taiga` | 36–40 |
| `minecraft:old_growth_spruce_taiga` | 37–41 |
| `minecraft:snowy_taiga` | 38–42 |
| `minecraft:snowy_plains` | 39–43 |
| `minecraft:frozen_river` | 40–44 |
| `minecraft:snowy_beach` | 41–45 |
| `minecraft:frozen_ocean` | 42–46 |
| `minecraft:deep_frozen_ocean` | 43–47 |
| `minecraft:grove` | 45–49 |
| `minecraft:ice_spikes` | 47–51 |
| `minecraft:snowy_slopes` | 49–53 |
| `minecraft:frozen_peaks` | 52–56 |
| `minecraft:jagged_peaks` | 55–59 |

#### Zona 4: Nether — níveis 60–79

Esta é a zona do Nether, com florestas distorcidas e carmesins, vales de areia das almas, deltas de basalto e os Nether Wastes. Gera Detritos ancestrais com mais frequência que no vanilla

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:warped_forest` | 60–65 |
| `minecraft:crimson_forest` | 63–68 |
| `minecraft:nether_wastes` | 66–71 |
| `minecraft:soul_sand_valley` | 69–74 |
| `minecraft:basalt_deltas` | 75–79 |

#### Zona 5: End — níveis 81–89

Abrange as ilhas e regiões do End. Os níveis de 81 a 89 tornam esta dimensão uma área de fim de jogo; ela não recebe minérios controlados pela progressão de mineração.

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:small_end_islands` | 81–85 |
| `minecraft:end_barrens` | 82–86 |
| `minecraft:end_midlands` | 83–87 |
| `minecraft:the_end` | 84–88 |
| `minecraft:end_highlands` | 85–89 |

#### Zona 6: Deep Dark — níveis 92–97

O `deep_dark` é uma zona especial e extremamente perigosa, com níveis de 92 a 97. Ela representa o pico da progressão de biomas e é separada das regiões comuns por sua dificuldade e identidade própria.

| ID do bioma | Níveis |
| --- | ---: |
| `minecraft:deep_dark` | 92–97 |

Os IDs acima foram validados contra os dados de Minecraft 26.3 incluídos pelas dependências do projeto. Biomas não listados não recebem uma faixa inventada: usam o fallback configurado, por padrão 1–5.

### Estruturas e chefes

| Regra | ID/entidade | Níveis |
| --- | --- | ---: |
| Estrutura | `minecraft:fortress` | 63–67 |
| Estrutura | `minecraft:bastion_remnant` | 74–79 |
| Estrutura | `minecraft:trial_chambers` | 36–41 |
| Estrutura | `minecraft:end_city` | 84–89 |
| Estrutura | `minecraft:ancient_city` | 94–99 |
| Fixo | Ender Dragon | 90 |
| Fixo | Wither | 80 |
| Fixo | Warden | 100 |

As faixas de nível para estruturas e biomas são configuráveis no arquivo JSON `config/rpgsystem_areas.json`. Para adicionar ou alterar uma estrutura, basta incluir ou editar a entrada na seção `"structures"` do JSON informando os limites e a zona de mineração correspondente:

```json
{
  "structures": {
    "minecraft:trial_chambers": {
      "min_level": 36,
      "max_level": 41,
      "mining_zone": "ZONE_3"
    },
    "minecraft:monument": {
      "min_level": 45,
      "max_level": 55,
      "mining_zone": "ZONE_3"
    },
    "mod_id:estrutura_customizada": {
      "min_level": 50,
      "max_level": 60,
      "mining_zone": "NONE"
    }
  }
}
```

O sistema identifica a estrutura em que o mob nasce ou em que o chunk gera minérios usando diretamente a peça da estrutura (`StructureStart`) em que o bloco se encontra. Caso nenhuma estrutura esteja registrada para aquele ponto, a regra do bioma prevalece. Se a estrutura estiver configurada com `mining_zone: "NONE"`, nenhum minério controlado será gerado dentro das suas peças.

### Escalonamento de atributos

Os modificadores do Sistema RPG são transitórios, têm IDs próprios e são atualizados de forma idempotente; modificadores de outros mods não são removidos. A entidade sem determinado atributo simplesmente não recebe aquele bônus. Para um nível `N`, os padrões são:

- vida máxima: modificador multiplicado sobre o valor-base de `0,015 × (N − 1)`;
- dano de ataque: modificador multiplicado sobre o valor-base de `0,020 × (N − 1)`;
- armadura: `min(20, 0,250 × (N − 1))` pontos adicionados.

Em nível 100, isso equivale a +148,5% de vida baseada no atributo, +198% de dano baseado no atributo e no máximo +20 de armadura. Ao reaplicar o sistema, a vida atual nunca aumenta: apenas é limitada à nova vida máxima se necessário.

### Configuração

Na inicialização do servidor ou mundo, são gerados arquivos JSON em `config/`:
- `config/rpgsystem_areas.json`: Configuração central unificada de áreas (biomas e estruturas). Define os níveis mínimos/máximos (`min_level`, `max_level`) e a zona de mineração (`mining_zone`: `ZONE_1`, `ZONE_2`, `ZONE_3`, `ZONE_4` ou `NONE`) para cada bioma ou estrutura. Mobs e regras de mineração consultam este mesmo arquivo, permitindo adicionar novos biomas/estruturas (incluindo de outros mods) diretamente via JSON.
- `config/rpgsystem_mob_levels.json`: Ajustes finos de combate e escalonamento dos mobs (`fallback_range`, `health_per_level`, `damage_per_level`, `armor_per_level`, `armor_bonus_cap` e `entity_overrides`). Os limites de combate têm validação de segurança.
- `config/rpgsystem_xp.json`: Ajustes dos ganhos de XP para jogadores e ações de classe.

Reinicie o servidor/jogo após alterar os arquivos.

## Geração de minérios por zonas

A geração de minérios controlados usa `MiningZoneConfig`, que por sua vez obtém suas definições e zonas de mineração diretamente do `AreaConfig` (`config/rpgsystem_areas.json`). O mapa explícito de biomas e estruturas não possui fallback silencioso: um bioma/estrutura sem regra ou com `NONE` não recebe os minérios controlados.

| Zona | Minérios com geração normal | Minérios anteriores autorizados |
| --- | --- | --- |
| 1 — inicial | carvão, cobre | — |
| 2 — intermediária | ferro, redstone, lápis-lazúli | carvão e cobre, reduzidos |
| 3 — avançada | ouro e diamante | minérios das Zonas 1 e 2, reduzidos |
| 4 — Nether | detritos ancestrais | nenhum outro minério controlado |

Os níveis de mineração padrão são independentes e estão centralizados em `src/main/java/br/com/rpgsystem/mining/MiningZoneConfig.java`. A Zona 1 usa níveis 1–6, a Zona 2 19–34 e a Zona 3 41–60; o Nether não usa progressão. Estruturas têm regras explícitas: `ancient_city` e `trial_chambers` usam a Zona 3, `fortress` e `bastion_remnant` usam a Zona 4, e `end_city` não recebe minérios controlados. A estrutura só prevalece quando o ponto está dentro de uma peça gerada, nunca por proximidade.

### Valores-base

| Minério | Tentativas por variante | Tamanhos-base |
| --- | --- | --- |
| Carvão | 45 + 25 | vanilla |
| Cobre | 105 por variante, incluindo a variante de grande veio | vanilla |
| Ferro | 45 + 15 + 15 | 9 + 9 + 5 |
| Redstone | vanilla | vanilla |
| Lápis-lazúli | vanilla | vanilla |
| Ouro | vanilla | vanilla |
| Diamante | vanilla | vanilla |
| Detritos ancestrais | 2 + 1 | vanilla |

Diamante, ouro, lápis-lazúli e redstone mantêm seus placed features, variantes, alturas, distribuições e regras de substituição vanilla. Cobre, carvão e detritos também mantêm tamanhos vanilla; somente as tentativas solicitadas são substituídas. A geração é interceptada no ponto final de colocação do `FeaturePlacer`, portanto não há uma segunda cópia dos recursos vanilla.

### Progressão, pico e queda

Na zona de origem, o sistema calcula `t = clamp((L − Lmin) / (Lmax − Lmin), 0, 1)` usando o nível explícito do bioma/estrutura:

- frequência: `F = 0,75 + 0,75 × t`;
- tamanho: `S = 0,75 + 0,60 × t`.

Assim, o menor nível começa em 75%/75% e o maior nível real da zona atinge exatamente 150%/135%. Ao entrar em uma zona superior autorizada, a curva cai diretamente para 5% da frequência e 20% do tamanho-base e permanece nesses valores, sem recuperação nem acúmulo. Os percentuais são aplicados ao valor-base individual de cada minério, preservando a raridade relativa entre eles. O Nether é uma exceção independente: detritos usam 2 + 1 tentativas, seus tamanhos/distribuições vanilla e nenhum multiplicador de progressão.

As distribuições verticais, filtros de altura, variantes triangulares/uniformes, mecanismos especiais de cobre e regras de substituição não são reconstruídos. Os overrides em `data/minecraft/worldgen/placed_feature` alteram apenas as contagens necessárias; chunks já existentes não são lidos nem convertidos, então somente a geração natural de chunks novos é afetada.

Os cálculos e o cache de resolução por chunk são determinísticos no servidor e não usam o nível global do jogador. As zonas e níveis de mineração são gerenciados de forma centralizada pelo `rpgsystem_areas.json`, mantendo a configuração totalmente editável e consistente com o sistema de níveis de mobs.

### Etiqueta ao mirar e multiplayer

Quando a mira do jogador acerta uma criatura com nível, somente ela recebe a etiqueta renderizada acima do corpo: `Nome - Nv. N`. Uma nametag substitui somente `Nome`; o nível continua separado, não é incorporado ao nome persistente e não duplica ao renomear repetidas vezes. O cliente não toma decisões de progressão: recebe o valor sincronizado pelo servidor, funcionando tanto em singleplayer quanto em servidor dedicado com o mod nos dois lados.

### Limitações e testes

O ponto de nascimento é registrado ao inicializar o mob quando ele entra no mundo; há um fallback no tick para spawns diretos, spawners e mods que não disparem o evento de carga. Para criaturas preexistentes sem nível, isto é explicitamente a posição da primeira carga após a atualização. A deteção de estruturas usa peças geradas, portanto não faz busca por proximidade. A compilação não substitui testes dentro do jogo: validação visual da etiqueta, combate contra cada chefe e cenários reais de portal/spawner devem ser feitos no cliente/servidor Minecraft.

`./gradlew.bat test` verifica todas as 64 faixas de bioma e as cinco faixas/ordem de estruturas; `./gradlew.bat build` gera o JAR e executa esses testes.

### Etiqueta ao mirar e multiplayer

### Etiqueta ao mirar e multiplayer

Quando a mira do jogador acerta uma criatura com nível, somente ela recebe a etiqueta renderizada acima do corpo: `Nome - Nv. N`. Uma nametag substitui somente `Nome`; o nível continua separado, não é incorporado ao nome persistente e não duplica ao renomear repetidas vezes. O cliente não toma decisões de progressão: recebe o valor sincronizado pelo servidor, funcionando tanto em singleplayer quanto em servidor dedicado com o mod nos dois lados.

### Limitações e testes

### Limitações e testes

O ponto de nascimento é registrado ao inicializar o mob quando ele entra no mundo; há um fallback no tick para spawns diretos, spawners e mods que não disparem o evento de carga. Para criaturas preexistentes sem nível, isto é explicitamente a posição da primeira carga após a atualização. A deteção de estruturas usa peças geradas, portanto não faz busca por proximidade. A compilação não substitui testes dentro do jogo: validação visual da etiqueta, combate contra cada chefe e cenários reais de portal/spawner devem ser feitos no cliente/servidor Minecraft.

`./gradlew.bat test` verifica todas as 64 faixas de bioma e as cinco faixas/ordem de estruturas; `./gradlew.bat build` gera o JAR e executa esses testes.

## Guia para desenvolvimento e criação de conteúdo

Esta seção reúne os procedimentos voltados a quem vai estender o mod ou criar novos recursos. A descrição do conteúdo já implementado aparece antes, para que os exemplos de implementação não interrompam a apresentação das funcionalidades.

### Implementação de classes

Classes são implementadas em Java, pois definem identidade, regras de XP, perks e árvore de habilidades. A implementação atual é `FerreiroClass`, registrada em `RpgMod.onInitialize()`.

### Passo a passo: criar uma classe

1. Crie uma classe Java em `src/main/java/br/com/rpgsystem/classes/` estendendo `RpgClass`:

   ```java
   public final class GuerreiroClass extends RpgClass {
       private static final Identifier ID = RpgMod.id("guerreiro");
       @Override public Identifier id() { return ID; }
       @Override public String name() { return "Guerreiro"; }
       @Override public String description() { return "Especialista em combate corpo a corpo."; }
       @Override public List<Identifier> perkIds() { return List.of(); }
       @Override public int xpFor(RpgAction action) {
           return action == RpgAction.HOSTILE_MOB_KILL ? 10 : 0;
       }
       @Override public List<SkillDefinition> skillTree() { return List.of(); }
   }
   ```

2. Registre a classe em `RpgMod.onInitialize()`:

   ```java
   ClassRegistry.register(new GuerreiroClass());
   ```

3. O comando usa o caminho do ID, neste exemplo `/rpg class set guerreiro`. Para adicionar habilidades, retorne `SkillDefinition`s em `skillTree()`. Cada definição inclui ID, nome, descrição, custo, tipo, dependências, efeito e quantidade; o jogador precisa ter pontos suficientes e desbloquear as dependências primeiro. A tela e o armazenamento das habilidades são genéricos, mas os efeitos de habilidades precisam ser conectados às rotinas de jogo apropriadas.

### Passo a passo: criar e associar uma perk

1. Crie `src/main/resources/data/rpgsystem/perks/furia.json`:

   ```json
   {
     "name": "Fúria",
     "description": "Aumenta o dano após derrotar criaturas.",
     "effect": "melee_damage",
     "amount": 2.0
   }
   ```

2. Associe o ID do recurso à classe:

   ```java
   private static final Identifier FURIA = RpgMod.id("furia");
   @Override public List<Identifier> perkIds() { return List.of(FURIA); }
   ```

O JSON fornece nome, descrição, chave de efeito e quantidade. A interface mostra nome e descrição. Um efeito só funciona quando há código que reconhece sua chave; atualmente o tratamento de morte reconhece `heal_on_hostile_kill`, enquanto rotinas de crafting/ferraria implementam efeitos específicos do Ferreiro descritos abaixo.

### Passo a passo: criar um afixo

1. Crie `src/main/resources/data/<namespace>/affixes/<id>.json`, por exemplo `src/main/resources/data/rpgsystem/affixes/afiado.json`.
2. Preencha os campos obrigatórios `name`, `rarity`, ao menos uma regra de compatibilidade (`groups`, `tags` ou `items`) e `modifiers`:

   ```json
   {
     "name": "Afiado",
     "description": "Uma lâmina de fio excepcional.",
     "rarity": "raro",
     "groups": ["Swords"],
     "modifiers": [
       {
         "attribute": "minecraft:attack_damage",
         "amount": 3.0,
         "operation": "add_value",
         "slot": "mainhand"
       }
     ]
   }
   ```

   `description` é opcional e aparece no tooltip quando Shift está pressionado; sem ela, descrições antigas nos modificadores são usadas como compatibilidade. Cada modificador exige `attribute`, `amount`, `operation` e `slot`. Operações válidas: `add_value`, `add_multiplied_base` e `add_multiplied_total`. Para um multiplicador de 10%, use `0.10` com uma operação multiplicada.

3. Escolha uma raridade exatamente como aparece abaixo:

   | Valor JSON | Exibição | Cor |
   |---|---|---|
   | `comum` | Comum | Cinza |
   | `incomum` | Incomum | Verde |
   | `raro` | Raro | Azul-claro |
   | `epico` | Épico | Roxo |
   | `lendario` | Lendário | Dourado e negrito |
   | `mitico` | Mítico | Vermelho e negrito |

4. Defina os itens aceitos. As listas de `groups`, `tags` e `items` são combinadas como alternativas (`OU`):

   ```json
   {
     "groups": ["Swords", "Metal"],
     "tags": ["minecraft:chest_armor"],
     "items": ["minecraft:trident", "#minecraft:foot_armor"]
   }
   ```

   `groups` aceita nomes de grupo, interseções com espaço (`"Metal Swords"`) e tags precedidas por `#`; `tags` aceita IDs de tags; `items` aceita IDs completos de itens ou tags precedidas por `#`. Dentro de cada grupo JSON também é possível combinar itens, tags e outros grupos. Confira a seção [Grupos de itens](#grupos-de-itens) para criar regras reutilizáveis.

5. Execute `/reload` para recarregar afixos e grupos de itens após editar os recursos. Para validar no jogo, segure um item compatível e use `/rpg affix list`, `/rpg affix apply <id>` ou `/rpg affix roll`. Crafting e aprimoramentos na ferraria também podem rolar afixos automaticamente ao retirar o resultado.

Os afixos aparecem no tooltip acima dos atributos vanilla. O nome e a raridade também definem a cor/nome customizado do item; a descrição única aparece ao segurar Shift.

## Desenvolvimento

- Código comum: `src/main/java`; código cliente: `src/client/java`.
- Recursos de dados: `src/main/resources/data/<namespace>/`, organizados em `affixes/`, `item_groups/` e `perks/`.
- Recursos cliente, idioma e configuração de mixins: `src/main/resources/assets/` e `src/main/resources/rpgsystem.mixins.json`.
- Versões de Minecraft, loader e Fabric API: `gradle.properties`; dependências e compilação: `build.gradle`.

Para iniciar o ambiente de desenvolvimento, use `./gradlew.bat runClient` ou `./gradlew.bat runServer`. Para gerar o mod, use `./gradlew.bat build`.
