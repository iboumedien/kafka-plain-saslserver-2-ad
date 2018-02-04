package org.navit.common.security.authorization

import kafka.network.RequestChannel
import kafka.security.auth.*
import org.apache.kafka.common.acl.AclPermissionType
import org.apache.kafka.common.resource.ResourceType
import org.apache.kafka.common.security.auth.KafkaPrincipal
import org.slf4j.LoggerFactory

class LDAPAuthorizer : SimpleAclAuthorizer() {

    private val navAuthorizer = NAVAuthorizer()

    override fun authorize(session: RequestChannel.Session?, operation: Operation?, resource: Resource?): Boolean {

        // nothing to do if already authorized
        if (super.authorize(session, operation, resource)) return true

        val principal = session?.principal()
        val lOperation = operation?.toString()
        val host = session?.clientAddress()?.hostAddress
        val lResource = resource?.toString()

        log.warn("Authorization Start -  $principal trying $lOperation from $host on $lResource")

        //TODO ResourceType.GROUP - under change in minor version - CAREFUL!
        // Warning! Assuming no group considerations, thus implicitly always empty group access control lists
        if (resource?.resourceType()?.toJava() == ResourceType.GROUP) {
            log.info("Authorization End - $principal trying $lOperation from $host on $lResource is authorized")
            return true
        }

        //TODO AclPermissionType.ALLOW - under change in minor version - CAREFUL!
        // getBounded allow access control lists for resource and given operation
        val sacls = getAcls(resource).filter { it.operation() == operation && it.permissionType().toJava() == AclPermissionType.ALLOW }

        // switch to kotlin set, making testing easier
        var acls: Set<Acl> = emptySet()
        sacls.foreach { acls += it }

        // nothing to do if empty acl set
        if (acls.isEmpty()) {
            log.info("Authorization End - empty ALLOW ACL for [$lResource,$lOperation], is not authorized")
            return false
        }

        return navAuthorizer.authorize(
                session?.principal() ?: KafkaPrincipal(KafkaPrincipal.USER_TYPE,"ANONYMOUS"),
                acls
        ).let {
            when(it) {
                true -> log.info("Authorization End - $principal is authorized!")
                false -> log.info("Authorization End - $principal is not authorized!")
            }
            it
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LDAPAuthorizer::class.java)
    }
}