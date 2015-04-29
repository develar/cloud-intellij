

> Tutum does "volumes-from" associations based on the container name suffix.
  Therefore, web-1 gets its "volumes-from" from certs-1, and mq-1 also gets its "volumes-from" from certs-1,
  that is why web-1 fails to deploy with this message.
  
> We will fix this in the future, but it is not between our priorities in the short term.
  For now, you could split the "certs" service in two services, "web-certs" and "mq-certs", 
  and deploy each of them with the same tags as "web" and "mq" respectively.