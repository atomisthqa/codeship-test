## background

* polling mavenrepos periodically to refresh the set of repos that might have rug archives
* track an offset in dynamo for the last timestamp, per repo, when we have synced
* for new rug archvies, raise an event on kafka topic `rug_publish`
* not currently supporting the deletion of archives

## Rug Archive Change Events


Each time we notice that a new rug archive has gone into one of the maven repos we support, we will raise an event like this
on the `rug_publish` kafka topic:

```json
{
  "correlation-id": "this is a guid -- to setup context for downstream consumers"
  "archives": [{"repo": "rugs-release",
                "path": "atomist-rugs/travis-handlers/0.4.1",
                "name": "travis-handlers-0.4.1-metadata.json",
                "created": "2017-03-28T11:33:03.311Z"}],
  "url": "https://atomist.jfrog.io/atomist/T1L0VDKJP",
  "team-id": "T1L0VDKJP"
}
```

* multiple new rug archvies may be sent in a single event
* the `team-id` for a global repo might be something unrelated to slack (like `global`)
crap
crap
crap
