package db

import com.bryzek.apidoc.api.v0.models.{Error, User}
import lib.{Constants, Validation}
import anorm._
import javax.inject.{Inject, Singleton}
import play.api.db._
import play.api.Play.current
import play.api.libs.json._
import java.util.UUID
import java.sql.Connection
import org.mindrot.jbcrypt.BCrypt
import org.apache.commons.codec.binary.Base64

private[db] case class HashedPassword(hash: String)

sealed trait PasswordAlgorithm {

  /**
   * Uniquely identifies this password algorithm
   */
  def key: String

  /**
   * Hashes the provided String, returning the hashed value
   */
  def hash(password: String): HashedPassword

  /**
   * Check if a cleartext password is valid
   */
  def check(candidate: String, hashed: String): Boolean

}

case class BcryptPasswordAlgorithm(override val key: String) extends PasswordAlgorithm {

  private[this] val LogRounds = 13

  override def hash(password: String): HashedPassword = {
    val salt = BCrypt.gensalt(LogRounds)
    HashedPassword(BCrypt.hashpw(password, salt))
  }

  override def check(candidate: String, hashed: String): Boolean = {
    BCrypt.checkpw(candidate, hashed)
  }

}

/**
 * Used only when fetching unknown keys from DB but will fail if you try to hash
 */
private[db] case class UnknownPasswordAlgorithm(override val key: String) extends PasswordAlgorithm {

  override def hash(password: String): HashedPassword = {
    sys.error("Unsupported operation for uknown password hash")
  }

  override def check(candidate: String, hashed: String) = false

}

object PasswordAlgorithm {

  val All = Seq(
    new BcryptPasswordAlgorithm("bcrypt"),
    new UnknownPasswordAlgorithm("unknown")
  )

  val Latest = fromString("bcrypt").getOrElse {
    sys.error("Could not find latest algorithm")
  }

  val Unknown = fromString("unknown").getOrElse {
    sys.error("Could not find unknown algorithm")
  }

  def fromString(value: String): Option[PasswordAlgorithm] = {
    All.find(_.key == value)
  }

}

case class UserPassword(guid: UUID, userGuid: UUID, algorithm: PasswordAlgorithm, hash: String)

@Singleton
class UserPasswordsDao @Inject() () {

  private[this] val MinLength = 5

  private[this] val BaseQuery = """
    select guid::varchar, user_guid::varchar, algorithm_key, hash
      from user_passwords
     where deleted_at is null
  """

  private[this] val InsertQuery = """
    insert into user_passwords
    (guid, user_guid, algorithm_key, hash, created_by_guid, updated_by_guid)
    values
    ({guid}::uuid, {user_guid}::uuid, {algorithm_key}, {hash}, {created_by_guid}::uuid, {updated_by_guid}::uuid)
  """

  private[this] val SoftDeleteByUserGuidQuery = """
    update user_passwords
       set deleted_by_guid = {deleted_by_guid}::uuid, deleted_at = now()
     where user_guid = {user_guid}::uuid
       and deleted_at is null
  """

  def validate(password: String): Seq[Error] = {
    val lengthErrors = if (password.trim.length < MinLength) {
      Seq(s"Password must be at least $MinLength characters")
    } else {
      Seq.empty
    }

    Validation.errors(lengthErrors)
  }

  def create(user: User, userGuid: UUID, cleartextPassword: String) {
    DB.withTransaction { implicit c =>
      softDeleteByUserGuid(c, user, userGuid)
      doCreate(c, user.guid, userGuid, cleartextPassword)
    }
  }

  private[db] def doCreate(
    implicit c: java.sql.Connection,
    creatingUserGuid: UUID,
    userGuid: UUID,
    cleartextPassword: String
  ) {
    val errors = validate(cleartextPassword)
    assert(errors.isEmpty, errors.map(_.message).mkString("\n"))
    
    val guid = UUID.randomUUID
    val algorithm = PasswordAlgorithm.Latest
    val hashedPassword = algorithm.hash(cleartextPassword)

    SQL(InsertQuery).on(
      'guid -> guid,
      'user_guid -> userGuid,
      'algorithm_key -> algorithm.key,
      'hash -> new String(Base64.encodeBase64(hashedPassword.hash.getBytes)),
      'created_by_guid -> creatingUserGuid,
      'updated_by_guid -> creatingUserGuid
    ).execute()
  }

  private[this] def softDeleteByUserGuid(implicit c: Connection, user: User, userGuid: UUID) {
    SQL(SoftDeleteByUserGuidQuery).on('deleted_by_guid -> user.guid, 'user_guid -> userGuid).execute()
  }

  def isValid(userGuid: UUID, cleartextPassword: String): Boolean = {
    findByUserGuid(userGuid) match {
      case None => false
      case Some(up: UserPassword) => {
        up.algorithm.check(cleartextPassword, up.hash)
      }
    }
  }

  private[db] def findByUserGuid(userGuid: UUID): Option[UserPassword] = {
    val sql = Seq(
      Some(BaseQuery.trim),
      Some("and user_passwords.user_guid = {guid}::uuid")
    ).flatten.mkString("\n   ")

    val bind = Seq[Option[NamedParameter]](
      Some('guid -> userGuid)
    ).flatten

    DB.withConnection { implicit c =>
      SQL(sql).on(bind: _*)().toList.map { row =>
        UserPassword(
          guid = UUID.fromString(row[String]("guid")),
          userGuid = UUID.fromString(row[String]("user_guid")),
          algorithm = PasswordAlgorithm.fromString(row[String]("algorithm_key")).getOrElse(PasswordAlgorithm.Unknown),
          hash = new String(Base64.decodeBase64(row[String]("hash").getBytes))
        )
      }.toSeq.headOption
    }
  }

}
