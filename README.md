# Projet "Wish-list collaborative" (ex-WBocal)

## Ce que fait l'application

- Côté serveur (Scala + ZIO HTTP) :
  - Gère une liste de souhaits en mémoire (CRUD complet : création, lecture, modification, suppression).
  - Fournit un endpoint pour tirer un souhait au hasard (`/random`).
  - Diffuse en temps réel les nouveaux souhaits avec WebSocket (`/stream`).

- Côté client (fichier `Stream_site.html`) :
  - Permet d'ajouter un souhait (`title`, `details`, `priority`).
  - Affiche en temps réel les nouveaux souhaits reçus du serveur.
  - Contient une zone de test CRUD pour lister, lire, modifier ou supprimer des souhaits.

## Lancer le serveur Scala

1. Ouvrir un terminal dans le dossier racine du projet (contenant `build.sbt`).
2. Lancer la commande suivante :

```bash
sbt run
```

Le serveur démarre et écoute par défaut sur `http://localhost:8080` (fourni par `Server.default`).

### Configuration du port

- Vous pouvez override le port HTTP en passant une propriété JVM lorsque vous lancez sbt, par exemple :

```bash
sbt -Dhttp.port=8081 run
```

Le serveur lit le port par défaut via la configuration de `Server.default`.

Note on dependencies:
- This project uses `zio-http` `3.0.0-RC6` (a release candidate). Consider pinning to a stable release when available.

## Tester l'API (exemples)

- Lister tous les wishes :

```bash
curl http://localhost:8080/wishes
```

- Créer un wish :

```bash
curl -X POST http://localhost:8080/wish -H 'Content-Type: application/json' -d '{"title":"Offrir une plante","details":"Plante verte pour le salon","priority":2}'
```

- Récupérer un wish par index :

```bash
curl http://localhost:8080/wish/0
```

- Tirer un wish aléatoire :

```bash
curl http://localhost:8080/random
```

## Lancer le client (page HTML)

1. Ouvrir `Stream_site.html` avec votre navigateur (double-clic ou `File -> Open`).
2. Vérifier que le serveur Scala est lancé sur `http://localhost:8080`.
3. Ajouter un souhait via le formulaire ; les nouveaux souhaits s'affichent en temps réel via WebSocket.

## Tests

- Ce projet contient des tests unitaires et un test d'intégration minimal utilisant `zio-test` :
  - `src/test/scala/WishSpec.scala` — tests unitaires (JSON, Ref, Hub publish).
  - `src/test/scala/IntegrationSpec.scala` — test d'intégration qui démarre le serveur et vérifie `/ping` et `/wishes`.

Run:

```bash
sbt test
```

Integration tests:

```bash
sbt "testOnly *IntegrationSpec"  # start server and run integration checks
sbt "testOnly *WebSocketE2ESpec"  # run the e2e WebSocket test

## CI caching & running integration tests

- CI caches sbt and coursier artifacts to speed up runs. If you change `project/plugins.sbt`, `project/build.properties` or any `*.sbt` file, the cache key will change and dependencies will be re-resolved.
- To run integration tests locally (the CI runs `sbt test` by default):

```bash
sbt "testOnly *IntegrationSpec"    # starts the server and runs integration checks
sbt "testOnly *WebSocketE2ESpec"  # run the e2e WebSocket test
```

If you want CI to run integration tests as a separate job, open a PR and I can add an `integration` job to the workflow that runs only on scheduled or manual dispatch.
```

## Remarques et améliorations à venir

- Le stockage est uniquement en mémoire (`Ref[Vector[Wish]]`).
- Le serveur retourne `404` pour `/random` si la liste est vide.