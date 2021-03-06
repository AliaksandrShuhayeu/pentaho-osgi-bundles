package org.pentaho.proxy.creators.authenticationprovider;

import org.pentaho.platform.proxy.api.IProxyCreator;
import org.pentaho.proxy.creators.ProxyUtils;
import org.springframework.security.Authentication;
import org.springframework.security.GrantedAuthority;
import org.springframework.security.GrantedAuthorityImpl;
import org.springframework.security.providers.UsernamePasswordAuthenticationToken;
import org.springframework.security.providers.anonymous.AnonymousAuthenticationToken;
import org.springframework.util.ReflectionUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by nbaker on 8/31/15.
 */
public class AuthenticationProxyCreator implements IProxyCreator<Authentication> {

  private Logger logger = LoggerFactory.getLogger( getClass() );

  @Override public boolean supports( Class aClass ) {
    return ProxyUtils.isRecursivelySupported( "org.springframework.security.core.Authentication", aClass );
  }

  @Override public Authentication create( Object o ) {
    String className = o.getClass().getName();

    if ( "org.springframework.security.authentication.UsernamePasswordAuthenticationToken".equals( className ) ) {
      Method getCredentials = ReflectionUtils.findMethod( o.getClass(), "getCredentials" );
      Method getPrincipal = ReflectionUtils.findMethod( o.getClass(), "getPrincipal" );
      Method getAuthorities = ReflectionUtils.findMethod( o.getClass(), "getAuthorities" );

      try {
        Object credentials = getCredentials.invoke( o, new Object[] {} );
        Object principal = getPrincipal.invoke( o, new Object[] {} );
        Collection granted = (Collection) getAuthorities.invoke( o, new Object[] {} );

        List<GrantedAuthority> authorityList = new ArrayList<GrantedAuthority>();

        for ( Object oGrant : granted ) {
          Method getAuthority = ReflectionUtils.findMethod( oGrant.getClass(), "getAuthority" );
          Object auth = getAuthority.invoke( oGrant, new Object[] {} );
          authorityList.add( new GrantedAuthorityImpl( auth.toString() ) );
        }

        return new UsernamePasswordAuthenticationToken( principal, credentials,
            authorityList.toArray( new GrantedAuthority[ authorityList.size() ] ) );
      } catch ( IllegalAccessException e ) {
        logger.error( e.getMessage(), e );
      } catch ( InvocationTargetException e ) {
        logger.error( e.getMessage() , e );
      }
    } else if( "org.springframework.security.authentication.AnonymousAuthenticationToken".equals( className ) ) {

      Method getKeyHash = ReflectionUtils.findMethod( o.getClass(), "getKeyHash" );
      Method getDetails = ReflectionUtils.findMethod( o.getClass(), "getDetails" );
      Method getPrincipal = ReflectionUtils.findMethod( o.getClass(), "getPrincipal" );
      Method getAuthorities = ReflectionUtils.findMethod( o.getClass(), "getAuthorities" );

      try {

        getKeyHash.setAccessible( true );
        getDetails.setAccessible( true );
        getPrincipal.setAccessible( true );
        getAuthorities.setAccessible( true );

        Object keyHash = getKeyHash.invoke( o );
        Object details = getDetails.invoke( o );
        Object principal = getPrincipal.invoke( o );
        Object authoritiesObj = getAuthorities.invoke( o );

        List<GrantedAuthority> authorityList = new ArrayList<GrantedAuthority>();

        if( authoritiesObj != null && authoritiesObj instanceof Collection ) {

          for( Object authorityObj : ( Collection) authoritiesObj ) {

            Method getAuthority = ReflectionUtils.findMethod( authorityObj.getClass(), "getAuthority" );
            Object authority = getAuthority.invoke( authorityObj );
            if( authority != null ) {
              authorityList.add( new GrantedAuthorityImpl( authority.toString() ) );
            }
          }
        }

        AnonymousAuthenticationToken anonymousToken = new AnonymousAuthenticationToken( keyHash.toString(),
            principal, authorityList.toArray( new GrantedAuthority[ authorityList.size() ] ) );
        anonymousToken.setDetails( details );

        return anonymousToken;

      } catch ( IllegalAccessException e ) {
        logger.error( e.getMessage(), e );
      } catch ( InvocationTargetException e ) {
        logger.error( e.getMessage(), e );
      }
    }
    return null;
  }
}
