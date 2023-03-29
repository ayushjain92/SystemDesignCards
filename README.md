# System Design Cards

---------

## Hot Partitions
How to handle? For e.g., our hash is on `videoId` then how do we handle hot partitions to calculate view counts for a video? Happens in cases of viral videos!

#### Solution:
1. **Re-Hash**: Include timestamp in the key so that all instances of video will be evenly distributed among the partitions.
2. **Split Partition**: Split a hot partition into multiple partitions using consistent hashing.
1. `[BONUS]` **Dedicated Partition**: Create a dedicated partition to handle workloads from popular channels like Mr. Beast, PewDieDie, T-Series, etc.?

--------

## Replication
How to achieve?

#### Solution:
1. **Single-leader replication**: Each partition with a single leader.
2. **Leaderless replication**: Each host talk to each other using gossip protocol.
3. **Multi-leader replication**: Used for replicating data among different DCs. Each DC will have it's own leader node.

#### Trade-Offs:
1. Achieves Availability but latency suffers. 

------------


## Client Side Protocol
* netty


------------

## Testing Scalability
1. Load Testing: increase tps. Ex. Apache JMeter
2. Stress Testing: breaking point in the system, which resource will fail first. 
3. Soak Testing: Generate high load so that memory leaks can be identified.
4. Netflix Chaos Monkey: Brings down any random host from host pool at a given frequency so that any single breaking point can be identified. 


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

