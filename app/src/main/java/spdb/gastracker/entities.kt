package spdb.gastracker


class GasNetwork(id: Int, name: String) {
    val network_id: Int
    val network_name: String


    override fun toString(): String {
        return network_name
    }

    override fun equals(other: Any?): Boolean {
        return other is GasNetwork && (other as GasNetwork).network_id == network_id
    }

    init {
        network_id = id
        network_name = name
    }
}