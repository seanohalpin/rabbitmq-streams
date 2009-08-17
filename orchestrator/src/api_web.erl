%% @author Michael Bridgen <mikeb@lshift.net>
%% @copyright 2009 Michael Bridgen, LShift Ltd.

%% @doc Web server for Streams API.

-module(api_web).
-author('Michael Bridgen <mikeb@lshift.net>').

-export([start/1, stop/0, loop/2]).

-include("api.hrl").

%% External API

start(Options) ->
    {DocRoot, Options1} = get_option(docroot, Options),
    Loop = fun (Req) ->
                   ?MODULE:loop(Req, DocRoot)
           end,
    mochiweb_http:start([{name, ?MODULE}, {loop, Loop} | Options1]).

stop() ->
    mochiweb_http:stop(?MODULE).

%% Dispatch to static resource or facet
loop(Req, DocRoot) ->
    Path = Req:get(path),
    case re:split(Path, "/", [{parts, 4}]) of
        [<<>>, <<"static">> | _] ->
            handle_static(Path, DocRoot, Req);
        [<<>>, <<>>] ->
            handle_root(DocRoot, Req);
        [<<>>, Facet, ResourceType, Name] ->
            case check_resource_type(ResourceType) of
                {ok, ResourceTypeAtom} ->
                    handle_method(ResourceTypeAtom,
                                  binary_to_list(Facet),
                                  Name,
                                  Req);
                {error, invalid_resource_type} ->
                    Req:not_found()
            end;
        _ ->
            Req:not_found()
    end.

%% Internal API

% Supplied in skeleton
get_option(Option, Options) ->
    {proplists:get_value(Option, Options), proplists:delete(Option, Options)}.

json_response(Req, Code, JsonStructure) ->
    % Content negoitation here?
    Req:respond({Code,
                 [{content_type, "application/json"}],
                 rfc4627:encode(JsonStructure)}).

check_resource_type(<<"terminal">>) -> {ok, terminal};
check_resource_type(<<"pipeline">>) -> {ok, pipeline};
check_resource_type(_) -> {error, invalid_resource_type}.

% Adapted from RabbitHub
handle_static("/" ++ StaticFile, DocRoot, Req) ->
    case Req:get(method) of
        Method when Method =:= 'GET'; Method =:= 'HEAD' ->
            Req:serve_file(StaticFile, DocRoot);
        _ -> Req:respond({405, [{"Allow", "GET, HEAD"}], "Method not allowed"})
    end;
handle_static(_OtherPath, _DocRoot, Req) ->
    Req:respond({400, [], "Invalid path"}).

% TODO Server status
handle_root(DocRoot, Req) ->
    json_response(Req, 200, app_status()).

% dispatch to particular facet and resource
handle_method(ResourceTypeAtom, Facet, <<>>, Req) ->
    handle_index(ResourceTypeAtom, Facet, Req);
handle_method(ResourceTypeAtom, Facet, Name, Req) ->
    Req:respond({200, [], "Method call."}).

handle_index(pipeline, "model", Req) ->
    json_response(Req, 200, list_pipelines());
handle_index(ResourceTypeAtom, Facet, Req) ->
    Req:respond({404, [], "Not found."}).

app_status() ->
    {obj, [{"application", ?APPLICATION_NAME},
           {"version", ?APPLICATION_VERSION}]}.

list_pipelines() ->
    {obj, [pipeline_pair(P) || P <- streams:all_pipelines(?FEEDSHUB_STATUS_DBNAME)]}.

pipeline_pair(Row) ->
    {ok, Id} = rfc4627:get_field(Row, "key"),
    {ok, PipelineDesc} = rfc4627:get_field(Row, "value"),
    PipelineUrl = api_url(model, pipeline, binary_to_list(Id)),
    {PipelineUrl, PipelineDesc}.

% TODO Check the facet and resource type
api_url(Facet, ResourceType, Id) ->
    "/" ++ mochiweb_util:join([atom_to_list(Facet), atom_to_list(ResourceType), Id], "/").
