#!/usr/bin/perl

# Scrape band descriptions from the Lowlands HTML schedule. Sine the HTML
# is otherwise so messed up I'm not going to bother understanding the rest.
# (post-midnight things are actually listed first and with the wrong date,
# for example. :-/)

use Net::HTTP;

# Super aggressive HTTP caching to save my time and their server. :-P
dbmopen( %wwwcache, 'wwwcache', 0644 );
dbmopen( %descs, 'descriptions', 0644 );

$day = wget( 'http://www.lowlands.nl/blokkenschema.php?dag=vrijdag' );
#my @stages = split( /<li class="stage/, $day );
#shift( @stages );
#
#foreach $shtml ( @stages )
#{
#	my( $tent ) = $shtml =~ /.*?<h3>(.*?)<\/h3>/;
#	print( "$tent\n" );
#}

while ($day =~ /<li class="scheme-act" id="act(\d+)".*?<span class="summary">(.*?)</sg) {
	my($id, $name) = ($1, $2);
	print("$id $name\n");
	$desc = wget('http://www.lowlands.nl/programme-act.php?id=' . $id);
	#print $desc;
	my($key, $value) = $desc =~ /^.*?<h2>(.*?)<.*?<div class="act-content">(.+?)<\/?(?:p|div)>/s;
	$key = lc($key);
	$value =~ s/(\s+|<.*?>)+/ /g;
	$descs{'desc/'.$key} = $value;
	if ($desc =~ /<li class="act-website"><a href="(.*?)"/) {
		$descs{'www/'.$key} = $1;
	}
	#print("'$key'\n$'value'\n");
	
	$httpkey = $key;
	$httpkey =~ s/ /+/g;
	if (wget('http://www.last.fm/music/' . $httpkey, 'HEAD')) {
		#print('Detected last.fm page: ' . 'http://last.fm/music/' . $httpkey . "\n");
		$descs{'lastfm/'.$key} = 'http://www.last.fm/music/' . $httpkey;
	}
}

sub wget($;$)
{
	my( $url, $meth ) = @_;
	my( $host, $loc ) = $url =~ m,^(?:http://)(.*?)(/.*)$,;
	my( $r, $s );
	
	if( !defined( $meth ) )
	{
		$meth = 'GET';
	}
	
	if( defined( $wwwcache{$meth.'/'.$url} ) )
	{
		return $wwwcache{$meth.'/'.$url};
	}
	
	my $h = Net::HTTP->new( Host => $host ) or return( '' );
	$h->write_request( $meth => $loc,
	                   'User-Agent' => 'Lynx/2.8.4rel.1 libwww-FM/2.14',
	                   'Accept' => 'text/html, text/plain, image/png, image/jpeg, image/gif, text/xml, text/*, */*;q=0.01',
	                   'Accept-Language' => 'nl, en' ) or return( '' );
	my $code = $h->read_response_headers or return( '' );
	if( $code == 200 )
	{
		if( $meth eq 'HEAD' )
		{
			$r = 1;
		}
		else
		{
			while( $h->read_entity_body( $s, 1024 ) )
			{
				$r .= $s;
			}
		}
		$wwwcache{$meth.'/'.$url} = $r;
		return( $r );
	}
	else
	{
		if( $meth eq 'HEAD' )
		{
			$wwwcache{$meth.'/'.$url} = 0;
			return( 0 );
		}
		else
		{
			return( '' );
		}
	}
}

dbmclose( %wwwcache );
