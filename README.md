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
1. Load Testing: increase tps
2. Stress Testing: breaking point in the system.
3. Soak Testing: Generate high load so that memory leaks can be identified.
4. Netflix Chaos Monkey: Brings down any random host from host pool at a given frequency so that any single breaking point can be identified. 


------------

## Monitoring

1. TPS
2. CPU
3. Errors
4. 