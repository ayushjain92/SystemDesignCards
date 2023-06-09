# System Design Cards

---------
## Concurrency 

1. [ConcurrentHashMap](https://itsromiljain.medium.com/curious-case-of-concurrenthashmap-90249632d335)
2. Distributed Locks
2. Versioned/Conditional Writes in db

If lock causes issues then partition. When reddit was getting a lot of upvotes for their trending posts then they partitioned their upvote queries. 

### Concurrency based on cores

| Core        | How Concurrency Works? | Examples                                                                                                                                                                                         | 
|-------------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Single Core | Context Switching      | ![Context Switch](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*_HglrgsHLrFrSxaFfGw8fA.png) ![](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*E3lhTuU_P3bePvL6Nfwf_A.jpeg) |
| Multi-Core  | Parallelism            | ![Parallelism](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*QbyO_eNcYHw8cUpvVR5AZw.jpeg)                                                                                             |                                                                                       

### LLD

#### ConcurrentHashMap
![ConcurrentHashMap](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*U3oE8gg95rTulEJQGG10GQ.png)
```java

/**
new ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel);
 Initial capacity of map is 100 which means ConcurrentHashMap will make sure it has space for adding 100 key-value pairs after creation.

 LoadFactor is 0.75f which means when average number of elements per map exceeds 75 (initial capacity * load factor = 100 * 0.75 = 75) at that time map size will be increased and existing items in map are rehashed to put in new larger size map.

 Concurrency level is 10, it means at any given point of time Segment array size will be 10 or greater than 10, so that 10 threads can able to write to a map in parallel.
 **/
ConcurrentHashMap map = new ConcurrentHashMap(100, 0.75f, 10);
```


#### CompletableFuture
```java

CompletableFuture.supplyAsync(() -> getUser(userId))
        .thenApply(CreditRatingService::getCreditRatingSystem1)
        .thenAccept(System.out::println);

CompletableFuture.supplyAsync(() -> getUser(userId))
        .thenApplyAsync(CreditRatingService::getCreditRatingSystem1)
        .thenAcceptAsync(System.out::println);
```

1. The difference is in the `async` suffix on the method names. The methods without async execute their task in the same thread as the previous task. So in the first example, all getUser, getCreditRating and println are executed in the same thread. It’s OK, it’s still a thread from the fork-join pool, so the main thread is not blocked.
2. The second variant always submits the succeeding task to the pool, so each of the tasks can be handled by different thread. The result will be the same, the first variant is a bit more effective due to less thread switching overhead. It does not make any sense to use the async variant here

`applyAsync` variant is used for the below use-cases:

```java
CompletableFuture<User> user = CompletableFuture.supplyAsync(() -> getUser(userId));

CompletableFuture<CreditRating> rating1 = 
    user.thenApplyAsync(CreditRatingService::getCreditRatingSystem1);
CompletableFuture<CreditRating> rating2 = 
    user.thenApplyAsync(CreditRatingService::getCreditRatingSystem2);

rating1
    .thenCombineAsync(rating2, CreditRating::combine)
    .thenAccept(System.out::println);
```

1. Since we want to do it in parallel, we have to use at least one async method. Without async, the code would use only one thread so both credit rating tasks would be executed serially.
2. We have added combine phase that waits for both credit rating tasks to complete. It’s better to make this async too, but from different reason. Without async, the same thread as in rating1 would be used. But we do not want to block the thread while waiting for the rating2 task. You want to return it to the pool and get a thread only when it is needed.
3. When both tasks are ready, we can combine the result and print it in the same thread, so the last thenAccept is without async.

```java
CompletableFuture.supplyAsync(() -> {
    try {
        // divide by 0;
    } catch (Exception e) {
        throw new RuntimeException("err", e);
    }}).thenAcceptAsync().exceptionally(e -> {
            System.err.println("Error! " + e.getMessage());
            return null;
});
```

##### CompletableFuture v/s ExecutorService
*ExecutorService:*
```java
public ReturnSomething parent(){
    child();
    ...//rest to UI
}

private void child() {
  ExecutorService executorService = Executors.newFixedThreadPool(3);
  
  executorService.submit( () -> { 
      MyFileService.service1();
  });
  executorService.submit(() -> {
        MyFileService.service2();
  });
  executorService.submit(() -> {
        MyFileService.service3();
  });
}
```
*CompletableFuture:*
```java
public ReturnSomething parent() {
    child();
    ...//rest to UI
}

private void child() {
    CompletableFuture.supplyAsync(() ->  MyFileService.service1();
    CompletableFuture.supplyAsync(() ->  MyFileService.service2();
    CompletableFuture.supplyAsync(() ->  MyFileService.service3();
}
```

Functionally, the above 2 approaches are more or less the same:you submit your tasks for execution; you don't wait for the result.

Technically, however, there are some subtle differences:
1. In the second approach, you didn't specify an executor, so it will use the **common ForkJoinPool**. You would have to pass an executor as second argument of supplyAsync() if you don't want that;
1. The CompletableFuture API allows to easily chain more calls with thenApply(), thenCompose() etc. It is thus more flexible than the simple Future returned by ExecutorService.submit();
1. Using CompletableFuture allows to easily return a future from your child() method using return CompletableFuture.allOf(the previously created futures).

###### Better Hybrid Approach:
```java
CompletableFuture.supplyAsync(MyFileService::service1, executorService);
CompletableFuture.supplyAsync(MyFileService::service2, executorService);
CompletableFuture.supplyAsync(MyFileService::service3, executorService);
```
We should never use common ForkJoinPool for blocking I/O calls like calling a micro-service/database calls etc. In general, there are two types of tasks: computational and blocking. If you have more than three available CPUs, then your commonPool is automatically sized to two threads and you can very easily block execution of any other part of your system that uses the commonPool at the same time by keeping the threads in a blocked state.

### References
[1](https://blog.krecan.net/2013/12/25/completablefutures-why-to-use-async-methods/), [2](https://codeflex.co/java-multithreading-completablefuture-explained/), [3](https://stackoverflow.com/questions/27723546/completablefuture-supplyasync-and-thenapply), [4](https://stackoverflow.com/questions/52303472/executorservice-vs-completablefuture)

---------

## Hot Partitions
How to handle? For e.g., our hash is on `videoId` then how do we handle hot partitions to calculate view counts for a video? Happens in cases of viral videos!

#### Solution:
1. **Re-Hash**: Include timestamp in the key so that all instances of video will be evenly distributed among the partitions.
2. **Split Partition**: Split a hot partition into multiple partitions using consistent hashing. 2-layer cluster where a single *Proxy cluster* re-directs to actual cluster.
1. `[BONUS]` **Dedicated Partition**: Create a dedicated partition to handle workloads from popular channels like Mr. Beast, PewDieDie, T-Series, etc.?

--------

## Replication
How to achieve?

#### Solution:
1. **Single-leader replication**: Each partition with a single leader.
2. **Leaderless replication**: Each host talk to each other using gossip protocol.
3. **Multi-leader replication**: Used for replicating data among different DCs. Each DC will have it's own leader node.
1. `[BONUS]` **Redundancy**: Fast Reads. In the consistent hashing ring, every node should have a copy of data from it's previous node. This helps in achieving low latency when a node fails and the node in front of it start serving its request.


#### Distributed Consensus in cases of node failures:
1. Quoram
2. 

#### Trade-Offs:
1. Achieves Availability but latency suffers. 

------------


## Client Side Protocol
* netty


------------

## Testing Scalability

1. **Load Testing**: increase tps
2. **Stress Testing**: breaking point in the system.
3. **Soak Testing**: Generate high load so that memory leaks can be identified.
4. **Netflix Chaos Monkey**: Brings down any random host from host pool at a given frequency so that any single point of failures can be identified.


------------

## Monitoring

1. Latency
2. Error
3. Traffic
4. Saturation 

-------

## How to handle Thundering Herds/Cascading Failures?

*Cascading Failures*: when one server fails, other servers start failing too resulting in a domino effect of failing each server. 

1. **Rate Limiting**: Assign each server a request queue with a pre-determined capacity. Queue Capacity can be determined based on server’s compute power, cpu, etc. 
2. **Leasing Cache Requests**: Lease get calls to Memcache returns following responses:
    1. `FILL`: Request will get the latest value from db and fill the cache. 
    2. `WAIT/USE_STALE`: Any concurrent request read old value from cache or wait for the new value to be filled. 
    3. Lesser load on the primary database. 
    4. Instagram/Facebook uses heavily to display mostly stale likes/view counts/etc. 
3. **Viral** :
    1. Rate limiting + Auto scaling 
4. **Black Friday**: 
    1. Do pre-scale based on load factor. 
    2. Also attach auto scaling. 
5. **Job Scheduling**:
    1. Always In batches if possible + sleep in between request calls. 
6. **Popular Post**:
    1. Twitter do not pre-populate celebrity posts in user’s feed cache. Get calls for Posts from celebrities I follow are made at runtime when I open my app and those cards are merged in my cache at some pre-calculated positions.
    2. If you must send notifications, then add jitter, YouTube uses jitter to send notifications to followers of bell icon for popular pages
7. **Approximation**: Approximations are used for displaying like/view counts since those are metadata and not core feature of the product. 
8. If scale is very high, invest in hardware. 


-----

## Consistency 

Two generals problem

1. Leader-Follower roles. 
2. 2-Phase Commit. 
1. Quoram
3. DynamoDB write requests writes to the leader node synchronously and start a worker pool to write to the follower nodes(async wait). As soon as any one follower node writes the data successfully, client is returned successful response.



------------

## Event Driven Architecture

1. Used in multi-player online gaming algorithms. For example, when we take headshots in Counter Strike then the event is sent with timestamp of the shot and then the backend server matches if the position of player-B was matching with the shot direction/location for a given timestamp.  


#### Trade-Offs:
1. High availability v/s low consistency
2. Easy to rollback all events after timestamp T.
3. Easy to migrate to new service, new service need to consume events. Gateway services like BGCVGS/ABDMSil need to store timestamps.
4. Transaction Guarantee
   1. Atleast once: invoice email, should send atleast once
   2. Atmost once: welcome email, do not care if we do not send it as well.
5. Queue overfill: Priority Queues for high bandwidth/tps events. P-0 Queue to handle Sev1/Sev2, P-1 Queue to handle workloads from production code, P-2 Queue to handle workloads from Customer Support/Production testing.
6. Difficult to move out of.


------------

## No-Sql v/s MySql

#### Trade-offs
##### Advantages of NoSQL
1. When `select *` is always needed.
2. Insertions require the whole blob. for e.g., 
3. Schema is flexible. adding new column is really easy. 
4. Horizontal partitions are inbuilt. more focused on availability (check replication).
5. Built for aggregations/metrics/analysis.

##### Disadvantages
1. Not built for updates.
7. ACID is not guaranteed. Transactions not guaranteed.
8. Not read optimized on a column. For e.g., difficult to get all employees with age>30.
9. Relations are not implicit.
10. Joins are expensive.

#### How No-SQL data is stored?
1. In log files. 
2. Data is kept in self sorted structures like (AVL/ Red-Black Trees) in memory, and once the memory is past some threshold value (say ~50kb), then the entire memtable(the self sorted trees) are dumped into a SSTable (on disk) which is efficient as the data is already sorted.

-----------------


### OSI Model

1. **Application Layer:** Protocols (FTP/HTTP(S)/SMTP/Telnet) used for n/w applications like Chrome,Email,Skype,etc.
1. **Presentation Layer:** Received data from application layer. Converts texts to binary format. Protocols for : Data Compression (video/images) + Conversion + Encryption Protocols(SSL).  
1. **Session Layer:** APIs to communicate to each other. Session Management + Authentication (username/password) + Authorization. 
1. **Transport Layer:** Reliability of communication. divides data into List<Segment>. 
   ```
      class TransportLayer {
         List<String> segmentIds;
         double dataTransmissionRate; // This tells server at what rate a client can accept the data from server like 10MBps (based on mobile n/w configurartions). Server makes a decision on data packets to be transmitted after looking at this value. There should be a retry + jitter mechnism here to increase this value so that max bandwidth can be defined be the client application?
      }
   
      class Segment {
         String segmentId;
         int seqNo; // packet sequence number
         Port port; // used to direct each data to correct server application.
         String data; // 
      }
   
      class Port {
         int sourcePort;
         int destinationPort;
      }
   ```
   Data corruption techniques are also used here. Retries are used for the data packets not matching checksums at this layer if we use TCP.
   1. Involved in Segmentation + Flow Control + Error Control 
1. **Network Layer**: Receives from Transport Layer. Routers reside here. Logical Addressing (IP Address) + Routing + Path Determination.
   ```
      class NetworkLayer {
         String sourceIP;
         String destinationIP;
         String segmentId; 
      }
   ```
   Path Determination: OSPF(Open shortest Path First), BGP(Border Gateway Protocol), IS-IS 
1. **Data Layer**: Mac Addresses are assigned to sender/receiver of each data packet. Mac Address is Embedded in NIC Card by manufacturer.
1. **Physical Layer**: Converts binary frames to signals.


---------


### Why do we need a persistent TCP connection in cases of live streams/chat applications etc.?

To send traffic to clients, the server and client must establish a persistent TCP connection. The need for a persistent connection is because the stream must continue to come in, and creating new connections each time would take more time and make the video not remain live. To maintain a persistent connection, there are various technologies available, such as **Web Sockets**.


---------

## Storage

### Disk — RAID and Volume

![RAID](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*fRhgj6yQ_xkwwv8V6U9X3g.png)



| RAID    | Examples                                                                                                                       | Number of Drives needed | Read Performance                                                 | Write Performance          | Capacity Utilization | Fault Tolerance | 
|---------|--------------------------------------------------------------------------------------------------------------------------------|-------------------------|------------------------------------------------------------------|----------------------------|----------------------| ----------------------|
| RAID 0  | 1. Data stored in local hard drives. <br/> 2. High end workstations.<br/>3. data logging. <br/>4. real-time data rendering.    | 2                       | High                                                             | High                       | 100%                 | None |
| RAID 1  | 1. Data stores in local hard drives and also backed up on GoogleDrive <br/> 2. Operating Systems<br/>3. Transaction Databases. | 2                       | High                                                             | Medium (need to replicate) | 50%                  | Single-drive failure |
| RAID 10 | 1. Fast Databases<br/>2. File Servers<br/>3. Application Servers.                                                              | 4                       | High                                                             | Medium | 50%                  | Upto 1-disk failure in each subarray |
| RAID 5  | 1. Data Warehouse<br/>2. Archiving                                                                                             | 3                       | Low (data needs to be clubbed using parity from multiple drives) | Low | 67% - 94%            | Single-drive failure |
| RAID 6  | 1. Data archive backup to disk<br/>2. High availability solutions<br/>3.  Servers with large-capacity requirements.            | 4                       | Low                                                              | Low | 50% - 88%            | 2-drive failure |


#### How is data stored using Parity?
tbd.

![Volume](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*3fC-k3gD4sEsPAMKHkYsPg.png)


### File Storage, Block Storage, and Object Storage

![Storage](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*Fx29FK7N_Uq3ecXNMUL5Ng.png)

| Storage | Overview                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Scaling                                      | Examples                                                                                                                                                                                                                                                                                                                                               | AWS                               |  
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| File    | 1. Store data as files and present it to its final users as a hierarchical directories structure. <br/> 2. File Storage is the oldest and most widely used data storage system for direct (DAS) and NAS systems.<br/>                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Horizontally. Vertical scaling not possible. | YouTube Videos can be stored in a distributed file storage system like HDFS or GlusterFS. Spotify uses object storage to store songs.                                                                                                                                                                                                                  | Amazon Elastic File System (EFS)  |
| Block   | 1. Block storage chops data into blocks (chunks) and stores them as separate pieces. Each block of data is given a unique identifier, which allows a storage system to place the smaller pieces of data wherever it is most convenient. That means that some data can be stored in a Linux environment and some can be stored in a Windows unit.  <br/> 2. Block storage is often configured to decouple the data from the user’s environment and spread it across multiple environments that can better serve the data. And then, when data is requested, the underlying storage software reassembles the blocks of data from these environments and presents them back to the user.<br/>3. Data is stored in blocks of uniform size, it is ideal for data that needs to be accessed and modified frequently as it provides low-latency. <br/>it is expensive, complex, and less scalable compared with File Storage.<br/>4. It also has limited capability to handle metadata, which means it needs to be dealt with at the application or database level. | -                                            | 1. File hosting service like Dropbox, Google Drive, Onedrive: To store files, we can use Block storage in which files can be stored in small parts or chunks (say 4MB). Object Storage is used by Dropbox to store files.<br/> 2. Containers: Developers use block storage to store containerized applications on the cloud. <br/>3. Virtual machines  | Amazon Elastic Block Store (EBS)  | 
| Object  | 1. Storing vast amounts of unstructured data.<br/> 2. Object storage volumes work as modular units: each is a self-contained repository that owns: a) the data: images, videos, websites backups; b) a unique identifier (UID) that allows the object to be found over a distributed system; c) the metadata that describes the data: authors of the file, permissions set on the files, date on which it was created. The metadata is entirely customizable.<br/> 3. less expensive, but the objects can’t be modified — you have to write the object completely at once.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Object storage is cost-efficient: you only pay for what you use. It can scale easily, making it a great choice for public cloud storage.                                            | Amazon Simple Storage Service (Amazon S3)                                                                                                                                                                                                                                                                                                              | Medium                            | 50%                                                                                                                                                                                          | Upto 1-disk failure in each subarray |


#### When to use block-storage v/s object storage?

1. Object storage is typically used for large volumes of unstructured data, while block storage works best with transactional data and small files that need to be retrieved often.
2. Think of block storage as a compact parking garage with valet parking, and object storage as a massive, open parking lot with acres of spaces. The Block Storage Garage, as we can call it, allows drivers to quickly retrieve their cars; but it has limited space for vehicles, and expanding capacity would involve constructing a new garage and hiring more valets, which is expensive. The Object Storage Lot, in contrast, allows as many drivers to park as desired. However, some of the cars may end up at the far end of the parking lot, and it could take some time for drivers to retrieve them.
3. Block storage is fast, and it is often preferred for applications that regularly need to load data from the backend.
4. One of the biggest advantages of object storage is its cost. Storing data via object storage is usually less expensive than doing so in block storage. Block storage requires a fair amount of processing power so that data can be reassembled and read often, and this optimization for performance tends to make it more costly.

### HDFS (Hadoop Distributed File System)

HDFS is designed to reliably store very large files across machines in a large cluster. It stores each file as a sequence of blocks; all blocks in a file except the last block are the same size. The blocks of a file are replicated for fault tolerance (HDFS requires Block Storage).


-----------


## How to scale databases?

1. **Cache Database Queries:** Use appropriate caching strategies. Trade-offs:
   1. High cost for managing cache servers.
   2. More code maintenance (testing caches, upgrading caches, increasing decreasing cache size, etc.)
   3. More latency in cases of cache misses.
   4. More layer of monitoring needed on caches now. Introducing one more point of failure.
2. **Database Indexes:** Choose/Create indexes (primary+secondary) appropriately. Trade-Offs:
   1. High cost, entire data in each index gets replicated with new key. Creating too many indexes increases cost and write performances as well. 
   2. Consistency: Secondary indexes will be eventually consistent.
3. **Database Read Replication:** Create more read replicas for databases with huge read traffic. Trade-Offs:
   1. More servers more cost.
   2. If read replicas need to be synced consistently then more latencies in write otherwise there is a chance of reading stale data in cases of eventual consistency. We can implement quoram reads to avoid stale data issue but that will increase read latencies.
4. **Database Sharding:**
   1. Horizontal Sharding: ![horizontal-sharding](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*sm51Y09jiW9d0G8C-sFlJg.png)
   1. Verical Sharding: ![vertical-sharding](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*h_QdzTcQ78nTQJ-R6uEjXA.png)
   2. Having a sharded database architecture provides some pretty massive benefits, however, it is complex and has a high implementation and maintenance cost. This is definitely an option you’d want to consider after exhausting other scaling solutions as the ramifications of ineffective implementation can be quite severe.



Implementing scaling solutions introduces the following complexities:
1. Adding new features takes longer.
1. The system becomes more complex with more pieces and variables involved.
1. Code can be more difficult to test.
1. Finding and resolving bugs becomes harder.
![database-scaling](https://miro.medium.com/v2/resize:fit:1400/format:webp/1*E72uBT_C4sTjqWnjwIC1hQ.jpeg)

-----------------


## Resiliency

### Downstream resiliency
#### Timeout
When a network call is made, it’s best practice to configure a timeout to fail the call if no response is received within a certain amount of time. If the call is made without a timeout, there is a chance it will never return. Network calls that don’t return lead to resource leaks.

*How to configure timeouts?*
1. One way is to base it on the desired false timeout rate. For example, suppose we have a service calling another, and we are willing to accept that 0.1% of downstream requests that would have eventually returned a response time out (i.e., 0.1% false timeout rate). To accomplish that, we can configure the timeout based on the 99.9th percentile of the downstream service’s response time.


#### Retry: exponential backoff, retry amplification

```java

/**
For example, if the cap is set to 8 seconds, and the backoffCoeffiecient is 2 seconds, then the first retry delay is 2 seconds, the second is 4 seconds, the third is 8 seconds, and any further delay will be capped to 8 seconds.
 **/
delay = min(cap, backoffCoefficient * 2^attempt);

```
Although exponential backoff does reduce the pressure on the downstream dependency, it still has a problem. When the downstream service is temporarily degraded, multiple clients will likely see their requests failing around the same time. This will cause clients to retry simultaneously, hitting the downstream service with load spikes that further degrade it. To avoid this herding behavior, we can introduce random jitter into the delay calculation. This spreads retries out over time, smoothing out the load to the downstream service:

```java
//jitter
delay = random(0, min(cap, backoffCoefficient * 2^attempt));
```

**Async processes should not be retried always.** We can use a DLQ to retry async processes.

Having retries at multiple levels of the dependency chain can amplify the total number of retries — the deeper a service is in the chain, the higher the load it will be exposed to due to **retry amplification.**
![retry amplification](https://blog.pragmaticengineer.com/content/images/2022/09/dist_05.png)
And if the pressure gets bad enough, this behavior can easily overload downstream services. That’s why, when we have long dependency chains, we should consider retrying at a single level of the chain and failing fast in all the others.


#### Circuit breaker

The goal of the circuit breaker is to allow a sub-system to fail without slowing down the caller. To protect the system, calls to the failing sub-system are temporarily blocked. Later, when the sub-system recovers and failures stop, the circuit breaker allows calls to go through again.

Unlike retries, circuit breakers prevent network calls entirely, making the pattern particularly useful for non-transient faults. In other words, retries are helpful when the expectation is that the next call will succeed, while circuit breakers are helpful when the expectation is that the next call will fail.
**Example:** Amazon’s front page; if the recommendation service is unavailable, the page renders without recommendations. It’s a better outcome than failing to render the whole page entirely.

![Circuit Breaker](https://blog.pragmaticengineer.com/content/images/2022/09/dist_06.png)

How many failures are “enough to consider a downstream dependency down? How long should the circuit breaker wait to transition from the open to the half-open state? It really depends on the specific context; only by using data about past failures can we make an informed decision.

### Upstream resiliency
#### Load shedding
When the server detects that it’s overloaded, it can reject incoming requests by failing fast and returning a response with status code 503 (Service Unavailable). This technique is also referred to as load shedding.

*Load Sheding Algorithms:*
1. The server doesn’t necessarily have to reject arbitrary requests; for example, if different requests have different priorities, the server could reject only low-priority ones. 
2. Alternatively, the server could reject the oldest requests first since those will be the first ones to time out and be retried, so handling them might be a waste of time.


*Trade-Offs:*
1. Unfortunately, rejecting a request doesn’t completely shield the server from the cost of handling it. Depending on how the rejection is implemented, the server might still have to pay the price of opening a TLS connection and reading the request just to reject it. Hence, load shedding can only help so much, and if load keeps increasing, the cost of rejecting requests will eventually take over and degrade the server.



#### Load leveling
There is an alternative to load shedding, which can be exploited when clients don’t expect a prompt response. The idea is to introduce a messaging channel between the clients and the service. The channel decouples the load directed to the service from its capacity, allowing it to process requests at its own pace.

![Load leveling](https://blog.pragmaticengineer.com/content/images/2022/09/dist_07.png)

*Examples:* Submitting offers by Sellers.

*Trade-offs:*
1. It’s well suited to fending off short-lived spikes, which the channel smooths out. But if the service doesn’t catch up eventually, a large backlog will build up, which comes with its own problems.



### Rate limiting
1. Rate-limiting, or throttling, is a mechanism that rejects a request when a specific quota is exceeded. A service can have multiple quotas, e.g., for the number of requests or bytes received within a time interval. Quotas are typically applied to specific users, API keys, or IP addresses.
2. Rate-limiting is also used to enforce pricing tiers; if users want to use more resources, they should also be willing to pay more. This is how you can offload your service’s cost to your users: have them pay proportionally to their usage and enforce pricing tiers with quotas.

Although rate-limiting has some similarities with load shedding, they are different concepts. Load shedding rejects traffic based on the local state of a process, like the number of requests concurrently processed by it; rate-limiting instead sheds traffic based on the global state of the system, like the total number of requests concurrently processed for a specific API key across all service instances. And because there is a global state involved, some form of coordination is required.

### References
https://blog.pragmaticengineer.com/resiliency-in-distributed-systems/


